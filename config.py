import platform
import os

OS = platform.system()

match OS:
    case "Windows":
        #BASE_DIR = os.path.expandvars(r"%APPDATA%\WiresharkPipeline")
        TSHARK_PATH = r"C:\Program Files\Wireshark\tshark.exe"
        ES_BIN = r"C:\Program Files\Elastic\Elasticsearch\bin\elasticsearch.bat"
        MYSQL_BIN = r"C:\xampp\mysql\bin\mysqld.exe"
        MYSQL_HOST = "localhost"
        MYSQL_PORT = 3306
        ES_HOST = "localhost"
        ES_PORT = 9200

    case "Darwin":
        #BASE_DIR = os.path.expanduser("~/Library/Application Support/WiresharkPipeline")
        TSHARK_PATH = "/Applications/Wireshark.app/Contents/MacOS/tshark"
        ES_BIN = "/opt/homebrew/bin/elasticsearch"
        MYSQL_BIN = "/Applications/XAMPP/bin/mysql"
        MYSQL_HOST = "localhost"
        MYSQL_PORT = 3306
        ES_HOST = "localhost"
        ES_PORT = 9200

    case "Linux":
        #BASE_DIR = os.path.expanduser("~/.local/share/WiresharkPipeline")
        TSHARK_PATH = "/usr/bin/tshark"
        ES_BIN = "/usr/share/elasticsearch/bin/elasticsearch"
        MYSQL_BIN = "/usr/bin/mysql"
        MYSQL_HOST = "localhost"
        MYSQL_PORT = 3306
        ES_HOST = "localhost"
        ES_PORT = 9200

    case _:
        raise EnvironmentError(f"Unsupported OS: {OS}")

MYSQL_DB = "nsm"
MYSQL_USER = "root"
MYSQL_PASS = ""
MAX_BATCH = 1000
FLUSH_INTERVAL = 15.0
JAR_PATH = "target/wireshark-parser-1.0-SNAPSHOT.jar"




import shutil
import subprocess

def check_dependencies():
    errors = []

    # Java
    if shutil.which("java") is None:
        errors.append("[MISSING] Java — install JDK and ensure it's on PATH")
    else:
        ver = subprocess.run(["java", "-version"], capture_output=True, text=True)
        print(f"[OK] Java — {ver.stderr.splitlines()[0]}")

    # Python
    print(f"[OK] Python — {platform.python_version()}")

    # tshark
    if not os.path.exists(TSHARK_PATH):
        errors.append(f"[MISSING] tshark — expected at {TSHARK_PATH}")
    else:
        print(f"[OK] tshark — {TSHARK_PATH}")

    # JAR
    if not os.path.exists(JAR_PATH):
        errors.append(f"[MISSING] JAR — run `mvn package` to build it (expected at {JAR_PATH})")
    else:
        print(f"[OK] JAR — {JAR_PATH}")

    # MySQL binary
    if not os.path.exists(MYSQL_BIN):
        errors.append(f"[MISSING] MySQL — expected at {MYSQL_BIN}")
    else:
        print(f"[OK] MySQL binary — {MYSQL_BIN}")

    # Elasticsearch binary
    if not os.path.exists(ES_BIN):
        errors.append(f"[MISSING] Elasticsearch — expected at {ES_BIN}")
    else:
        print(f"[OK] Elasticsearch binary — {ES_BIN}")

    if errors:
        print("\n[ERROR] Dependency check failed:")
        for e in errors:
            print(f"  {e}")
        raise EnvironmentError("Missing dependencies — fix the above before starting the pipeline")

    print("[INFO] All dependencies OK\n")