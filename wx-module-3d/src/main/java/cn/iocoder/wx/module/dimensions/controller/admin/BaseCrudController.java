package cn.iocoder.wx.module.dimensions.controller.admin;

import org.springframework.web.bind.annotation.*;

import java.util.List;

public abstract class BaseCrudController<T, ID> {


    private BaseService<T, ID> service;

    /**
     * 创建一个新的实体
     *
     * @param entity 要创建的实体
     * @return 创建后的实体
     */
    @PostMapping
    public T create(@RequestBody T entity) {
        return service.save(entity);
    }

    /**
     * 根据ID获取实体
     *
     * @param id 实体的ID
     * @return 找到的实体
     */
    @GetMapping("/{id}")
    public T getById(@PathVariable ID id) {
        return service.findById(id);
    }

    /**
     * 获取所有实体
     *
     * @return 实体的列表
     */
    @GetMapping
    public List<T> getAll() {
        return service.findAll();
    }

    /**
     * 更新实体
     *
     * @param id    实体的ID
     * @param entity 要更新的实体
     * @return 更新后的实体
     */
    @PutMapping("/{id}")
    public T update(@PathVariable ID id, @RequestBody T entity) {
        return service.update(id, entity);
    }

    /**
     * 根据ID删除实体
     *
     * @param id 实体的ID
     */
    @DeleteMapping("/{id}")
    public void deleteById(@PathVariable ID id) {
        service.deleteById(id);
    }

    // BaseService 是一个抽象的服务层接口，需要你根据实际情况实现
    public interface BaseService<T, ID> {
        T save(T entity);
        T findById(ID id);
        List<T> findAll();
        T update(ID id, T entity);
        void deleteById(ID id);
    }
}
