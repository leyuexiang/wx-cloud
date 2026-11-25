"""容量发生器的有界接收进程：逐帧数据留在所属进程，仅共享固定统计缓冲。"""
import ctypes
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import runpy
import time
from types import SimpleNamespace


ERROR_CODES = ('unexpected-sequence', 'unexpected-scope', 'subscription-rejected',
               'incomplete-renewal', 'transport-error')


def partition_clients(clients, processes):
    """稳定分配全部连接，不增加连接数或单进程之外的总建连预算。"""
    if not 1 <= clients <= 50 or not 1 <= processes <= min(5, clients):
        raise ValueError('接收进程限一到五个，且不能超过客户端数')
    return [list(range(index, clients, processes)) for index in range(processes)]


class SharedMetricBuffers:
    def __init__(self, context, total):
        self.seen = context.RawArray('B', total + 1)
        self.histogram = context.RawArray('I', 60002)
        # 每个缓冲只由所属连接的接收线程写，父进程在回收前只读进度。
        self.stats = context.RawArray('I', 4)


class SharedErrors:
    def __init__(self, context):
        self.mask = context.RawValue('I', 0)

    def append(self, value):
        index = ERROR_CODES.index(value) if value in ERROR_CODES else ERROR_CODES.index('transport-error')
        self.mask.value |= 1 << index

    def values(self):
        return [code for index, code in enumerate(ERROR_CODES) if self.mask.value & (1 << index)]

    def __bool__(self):
        return bool(self.mask.value)

    def storage_bytes(self):
        return ctypes.sizeof(self.mask)


def receiver_process(module_path, config, state, rows, assignments, buffers, error_flags,
                     clock_buffers, total, stop, measuring, setup_gate, output):
    """身份只在启动管道及内存中，控制回执禁止携带令牌或异常正文。"""
    viewers, ready, cpu_start = [], False, None
    module = runpy.run_path(module_path)
    clock = module['SentClock'](total, clock_buffers)
    try:
        def create(assignment):
            number = assignment['number']
            with setup_gate:
                if stop.is_set():
                    raise RuntimeError('接收准备已取消')
                api = SimpleNamespace(user_id=assignment['userId'], token=assignment['token'])
                metric = module['DeliveryMetrics'](total, buffers[number])
                return module['Viewer'](api, config, state, rows, number, clock, stop,
                    assignment.get('ticket'), metric, error_flags[number])
        failures = []
        with ThreadPoolExecutor(max_workers=min(5, len(assignments))) as pool:
            for future in as_completed([pool.submit(create, assignment) for assignment in assignments]):
                try:
                    viewers.append(future.result())
                except Exception as error:
                    failures.append(type(error).__name__)
        if failures:
            output.send({'kind': 'error', 'types': sorted(set(failures))})
            return
        ready = True
        output.send({'kind': 'ready'})
        while not stop.wait(.05):
            if measuring.is_set() and cpu_start is None:
                cpu_start = time.process_time()
                output.send({'kind': 'measuring'})
    except Exception as error:
        output.send({'kind': 'error', 'types': [type(error).__name__]})
    finally:
        if not ready:
            stop.set()
        # 并行释放本进程连接，父进程另有所有接收进程共用的清理期限。
        with ThreadPoolExecutor(max_workers=5) as pool:
            list(pool.map(lambda viewer: viewer.close(), viewers))
        output.send({'kind': 'final', 'ready': ready,
            'cpuSeconds': max(0, time.process_time() - cpu_start) if cpu_start is not None else 0})
        output.close()


class ReceiverView:
    def __init__(self, number, metric, errors):
        self.number, self.metrics, self.error_flags = number, metric, errors
        self.user_id = None

    @property
    def renewal_acks(self):
        return self.metrics.renewal_acks

    @property
    def errors(self):
        return self.error_flags.values()


class ProcessReceiverGroup:
    def __init__(self, context, clients, processes, total, metric_factory,
                 setup_timeout=120, shutdown_timeout=20, worker_target=receiver_process):
        self.context, self.total = context, total
        self.partitions = partition_clients(clients, processes)
        if not 1 <= total <= 120000:
            raise ValueError('共享统计容量限一到十二万条')
        self.stop_event, self.measuring = context.Event(), context.Event()
        self.setup_gate = context.BoundedSemaphore(5)
        self.buffers = [SharedMetricBuffers(context, total) for _ in range(clients)]
        self.flags = [SharedErrors(context) for _ in range(clients)]
        self.viewers = [ReceiverView(index, metric_factory(total, self.buffers[index]), self.flags[index])
                        for index in range(clients)]
        self.setup_timeout, self.shutdown_timeout = setup_timeout, shutdown_timeout
        self.worker_target = worker_target
        self.processes, self.channels = [], []
        self.ready, self.measured, self.final, self.eof = set(), set(), {}, set()
        self.failures, self.stopped = set(), False

    def _poll(self):
        for index, channel in enumerate(self.channels):
            if index in self.eof:
                continue
            try:
                while channel.poll():
                    message = channel.recv()
                    if message.get('kind') == 'ready':
                        self.ready.add(index)
                    elif message.get('kind') == 'measuring':
                        self.measured.add(index)
                    elif message.get('kind') == 'final':
                        self.final[index] = message
                        if not message.get('ready'):
                            self.failures.add('receiver-setup-failed')
                    else:
                        self.failures.add('receiver-setup-failed')
            except (EOFError, OSError):
                self.eof.add(index)
                if index not in self.final:
                    self.failures.add('receiver-control-eof')

    def check_health(self):
        self._poll()
        if not self.stopped:
            for process in self.processes:
                if process.exitcode is not None:
                    self.failures.add('receiver-process-exited')
        return not self.failures

    def errors(self):
        return sorted(self.failures)

    def start(self, config, state, rows, assignments, clock):
        """先登记每个新进程，任何准备异常都回收已有子进程。"""
        try:
            if sorted(item['number'] for item in assignments) != list(range(len(self.viewers))):
                raise ValueError('接收分配必须恰好覆盖全部客户端')
            by_number = {item['number']: item for item in assignments}
            for numbers in self.partitions:
                incoming, outgoing = self.context.Pipe(duplex=False)
                process = self.context.Process(target=self.worker_target, args=(
                    str(Path(__file__).with_name('measure-capacity.py')), config, state, rows,
                    [by_number[number] for number in numbers],
                    {number: self.buffers[number] for number in numbers},
                    {number: self.flags[number] for number in numbers},
                    (clock.values, clock.counter), self.total, self.stop_event, self.measuring,
                    self.setup_gate, outgoing))
                try:
                    process.start()
                except BaseException:
                    incoming.close(); outgoing.close()
                    raise
                self.processes.append(process); self.channels.append(incoming)
                outgoing.close()
            for assignment in assignments:
                self.viewers[assignment['number']].user_id = assignment['userId']
            deadline = time.monotonic() + self.setup_timeout
            while len(self.ready) < len(self.processes):
                if not self.check_health():
                    raise RuntimeError('接收进程准备失败')
                if time.monotonic() >= deadline:
                    self.failures.add('receiver-setup-timeout')
                    raise RuntimeError('接收进程准备超时')
                time.sleep(.05)
            return self.viewers
        except BaseException:
            self.failures.add('receiver-setup-failed')
            self.stop()
            raise

    def begin_measurement(self):
        """全部子进程确认 CPU 采样起点后才允许发布，避免漏掉启动的第一批帧。"""
        self.measuring.set()
        deadline = time.monotonic() + 5
        while len(self.measured) != len(self.processes):
            if not self.check_health():
                raise RuntimeError('接收进程在采样准备期间退出')
            if time.monotonic() >= deadline:
                self.failures.add('receiver-measurement-timeout')
                raise RuntimeError('接收进程未确认采样起点')
            time.sleep(.01)

    def stop(self):
        """全局期限只等待一次，超时只终止本组持有的进程并强制判失败。"""
        if self.stopped:
            return sum(row.get('cpuSeconds', 0) for row in self.final.values())
        self.stopped = True
        self.stop_event.set()
        deadline = time.monotonic() + self.shutdown_timeout
        for process in self.processes:
            process.join(max(0, deadline - time.monotonic()))
        self._poll()
        for index, process in enumerate(self.processes):
            if process.is_alive():
                self.failures.add('receiver-shutdown-timeout')
                process.terminate(); process.join(3)
            if process.exitcode != 0:
                self.failures.add('receiver-process-failed')
            if index not in self.final:
                self.failures.add('receiver-result-missing')
        for channel in self.channels:
            channel.close()
        return sum(row.get('cpuSeconds', 0) for row in self.final.values())
