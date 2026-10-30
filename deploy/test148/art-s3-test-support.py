"""仅供测试服务器验收的S3签名工具；凭据只从服务器.env读取，不输出签名或密钥。"""
from pathlib import Path
import datetime,hashlib,hmac,urllib.request,urllib.parse,urllib.error
ROOT=Path("/opt/wx/editor-test148")
def credentials():
    env=dict(line.split("=",1) for line in (ROOT/".env").read_text().splitlines() if line and not line.startswith("#"))
    return env["WX_MINIO_ACCESS_KEY"],env["WX_MINIO_SECRET_KEY"]
def signing(secret,date):
    value=("AWS4"+secret).encode()
    for item in [date,"us-east-1","s3","aws4_request"]:value=hmac.new(value,item.encode(),hashlib.sha256).digest()
    return value

def request(method,path,body=b"",query=""):
    key,secret=credentials();now=datetime.datetime.now(datetime.timezone.utc);stamp=now.strftime("%Y%m%dT%H%M%SZ");date=stamp[:8]
    digest=hashlib.sha256(body).hexdigest();scope=date+"/us-east-1/s3/aws4_request"
    headers={"host":"127.0.0.1:9000","x-amz-content-sha256":digest,"x-amz-date":stamp}
    canonical=method+"\n"+path+"\n"+query+"\n"+"".join(k+":"+v+"\n" for k,v in sorted(headers.items()))+"\n"+";".join(sorted(headers))+"\n"+digest
    string="AWS4-HMAC-SHA256\n"+stamp+"\n"+scope+"\n"+hashlib.sha256(canonical.encode()).hexdigest()
    sig=hmac.new(signing(secret,date),string.encode(),hashlib.sha256).hexdigest()
    headers["Authorization"]="AWS4-HMAC-SHA256 Credential="+key+"/"+scope+", SignedHeaders="+";".join(sorted(headers))+", Signature="+sig
    req=urllib.request.Request("http://127.0.0.1:9000"+path+("?"+query if query else ""),data=body if method in ["PUT","POST"] else None,headers=headers,method=method)
    try:r=urllib.request.urlopen(req,timeout=15)
    except urllib.error.HTTPError as error:r=error
    return r.status,r.read()

def presigned(path,expires=2,age=0):
    key,secret=credentials();now=datetime.datetime.now(datetime.timezone.utc)-datetime.timedelta(seconds=age);stamp=now.strftime("%Y%m%dT%H%M%SZ");date=stamp[:8];scope=date+"/us-east-1/s3/aws4_request"
    pairs={"X-Amz-Algorithm":"AWS4-HMAC-SHA256","X-Amz-Credential":key+"/"+scope,"X-Amz-Date":stamp,"X-Amz-Expires":str(expires),"X-Amz-SignedHeaders":"host"}
    query=urllib.parse.urlencode(sorted(pairs.items()),quote_via=urllib.parse.quote)
    canonical="GET\n"+path+"\n"+query+"\nhost:192.168.10.148:9444\n\nhost\nUNSIGNED-PAYLOAD"
    string="AWS4-HMAC-SHA256\n"+stamp+"\n"+scope+"\n"+hashlib.sha256(canonical.encode()).hexdigest()
    sig=hmac.new(signing(secret,date),string.encode(),hashlib.sha256).hexdigest()
    return "https://192.168.10.148:9444"+path+"?"+query+"&X-Amz-Signature="+sig
