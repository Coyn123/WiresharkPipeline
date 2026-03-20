import subprocess
import time
import requests
import pymysql
from config import *

def start_elasticsearch():
    print("[INFO] Starting Elasticsearch...")
    subprocess.Popen(
        [f"{ES_BIN}"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )
    # wait for ES to be ready
    for i in range(30):
        try:
            res = requests.get("http://localhost:9200")
            if res.status_code == 200:
                print("[INFO] Elasticsearch is up")
                return True
        except requests.exceptions.ConnectionError:
            pass
        time.sleep(2)
    print("[ERROR] Elasticsearch failed to start")
    return False

def start_mysql():
    print("[INFO] Starting MySQL...")
    subprocess.run(
        [f"{MYSQL_BIN}", "start"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )
    # wait for MySQL to be ready
    for i in range(15):
        try:
            conn = pymysql.connect(host=f"{MYSQL_HOST}", user=f"{MYSQL_USER}", password=f"{MYSQL_PASS}", database=f"{MYSQL_DB}")
            conn.close()
            print("[INFO] MySQL is up")
            return True
        except pymysql.err.OperationalError:
            pass
        time.sleep(2)
    print("[ERROR] MySQL failed to start")
    return False

def start_services():
    mysql_ok = start_mysql()
    es_ok = start_elasticsearch()
    return mysql_ok and es_ok