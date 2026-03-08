# db.py
import mysql.connector
from mysql.connector import Error

class PacketDB:
    def __init__(self):
        try:
            #XAMPP Defaults
            self.conn = mysql.connector.connect(
                host="localhost",
                user="root",
                password="",
                database="nsm"
            )
            self.cursor = self.conn.cursor()
            print("[INFO] Connected to MySQL")
        except Error as e:
            print("[ERROR] MySQL Connection Failure:", e)
            raise

    def insert_packet(self, base):
        """
        Insert main packet info and return auto-increment packet_id.
        """
        try:
            sql = """
                INSERT INTO packets (timestamp, src_ip, dst_ip, ip_version, ip_proto, frame_len, ttl)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            vals = (
                base["timestamp"],
                base["src_ip"],
                base["dst_ip"],
                base["ip_version"],
                base["ip_proto"],
                base["frame_len"],
                base["ttl"],
            )
            self.cursor.execute(sql, vals)
            self.conn.commit()
            return self.cursor.lastrowid

        except Exception as e:
            print("[ERROR] Packet insert failed:", e)
            return None

    def insert_tcp(self, packet_id, packet):
        if not packet_id:
            return
        try:
            sql = """
                INSERT INTO tcp_packets (packet_id, src_port, dst_port, flags, seq, ack, window_size)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            vals = (
                packet_id,
                packet["src_port"],
                packet["dst_port"],
                packet["flags"],
                packet["seq"],
                packet["ack"],
                packet["window_size"],
            )
            self.cursor.execute(sql, vals)
            self.conn.commit()

        except Exception as e:
            print("[ERROR] TCP insert failed:", e)

    def insert_udp(self, packet_id, packet):
        if not packet_id:
            return
        try:
            sql = """
                INSERT INTO udp_packets (packet_id, src_port, dst_port, length)
                VALUES (%s, %s, %s, %s)
            """
            vals = (
                packet_id,
                packet["src_port"],
                packet["dst_port"],
                packet["length"],
            )
            self.cursor.execute(sql, vals)
            self.conn.commit()

        except Exception as e:
            print("[ERROR] UDP insert failed:", e)

    def insert_dns(self, packet_id, packet):
        if not packet_id:
            return
        try:
            sql = """
                INSERT INTO dns_packets (packet_id, query_name, query_type, answer_ip, rcode)
                VALUES (%s, %s, %s, %s, %s)
            """
            vals = (
                packet_id,
                packet["query_name"],
                packet["query_type"],
                packet["answer_ip"],
                packet["rcode"],
            )
            self.cursor.execute(sql, vals)
            self.conn.commit()

        except Exception as e:
            print("[ERROR] DNS insert failed:", e)

    def insert_tls(self, packet_id, packet):
        if not packet_id:
            return
        try:
            sql = """
                INSERT INTO tls_packets (packet_id, sni, tls_version, cipher_suite, handshake_type)
                VALUES (%s, %s, %s, %s, %s)
            """
            vals = (
                packet_id,
                packet["sni"],
                packet["tls_version"],
                packet["cipher_suite"],
                packet["handshake_type"],
            )
            self.cursor.execute(sql, vals)
            self.conn.commit()

        except Exception as e:
            print("[ERROR] TLS insert failed:", e)

    def close(self):
        """
        Close DB connection cleanly.
        """
        try:
            if self.cursor:
                self.cursor.close()
            if self.conn:
                self.conn.close()
            print("[INFO] MySQL connection closed")
        except Exception as e:
            print("[ERROR] Closing DB connection failed:", e)