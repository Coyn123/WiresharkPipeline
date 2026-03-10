import mysql.connector
from mysql.connector import Error

class PacketDB:
    def __init__(self):
        try:
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

    def insert_packet_batch(self, batch):
        if not batch:
            return []

        try:
            sql = """
                INSERT INTO packets
                (timestamp, src_ip, dst_ip, ip_version, ip_proto, frame_len, ttl)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            values = [
                (
                    p["timestamp"],
                    p["src_ip"],
                    p["dst_ip"],
                    p["ip_version"],
                    p["ip_proto"],
                    p["frame_len"],
                    p["ttl"],
                )
                for p in batch
            ]

            self.cursor.executemany(sql, values)
            self.conn.commit()

            first_id = self.cursor.lastrowid
            packet_ids = [first_id + i for i in range(len(batch))]
            return packet_ids

        except Exception as e:
            print("[ERROR] Packet batch insert failed:", e)
            return []
    def insert_light_batch(self, batch):
        if not batch:
            return []

        try:
            sql = """
                INSERT INTO packets_light
                (timestamp, src_ip, dst_ip)
                VALUES (%s, %s, %s)
            """
            values = [
                (
                    p["timestamp"],
                    p["src_ip"],
                    p["dst_ip"],
                )
                for p in batch
            ]

            self.cursor.executemany(sql, values)
            self.conn.commit()

            first_id = self.cursor.lastrowid
            packet_ids = [first_id + i for i in range(len(batch))]
            return packet_ids

        except Exception as e:
            print("[ERROR] packets_light batch insert failed:", e)
            return []

    def insert_tcp_batch(self, values):
        if not values:
            return
        try:
            sql = """
                INSERT INTO tcp_packets
                (packet_id, src_port, dst_port, flags, seq, ack, window_size)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            self.cursor.executemany(sql, values)
            self.conn.commit()
        except Exception as e:
            print("[ERROR] TCP batch insert failed:", e)

    def insert_udp_batch(self, values):
        if not values:
            return
        try:
            sql = """
                INSERT INTO udp_packets
                (packet_id, src_port, dst_port, length)
                VALUES (%s, %s, %s, %s)
            """
            self.cursor.executemany(sql, values)
            self.conn.commit()
        except Exception as e:
            print("[ERROR] UDP batch insert failed:", e)

    def insert_dns_batch(self, values):
        if not values:
            return
        try:
            sql = """
                INSERT INTO dns_packets
                (packet_id, query_name, query_type, answer_ip, rcode)
                VALUES (%s, %s, %s, %s, %s)
            """
            self.cursor.executemany(sql, values)
            self.conn.commit()
        except Exception as e:
            print("[ERROR] DNS batch insert failed:", e)

    def insert_tls_batch(self, values):
        if not values:
            return
        try:
            sql = """
                INSERT INTO tls_packets
                (packet_id, sni, tls_version, cipher_suite, handshake_type)
                VALUES (%s, %s, %s, %s, %s)
            """
            self.cursor.executemany(sql, values)
            self.conn.commit()
        except Exception as e:
            print("[ERROR] TLS batch insert failed:", e)

    def close(self):
        try:
            if self.cursor:
                self.cursor.close()
            if self.conn:
                self.conn.close()
            print("[INFO] MySQL connection closed")
        except Exception as e:
            print("[ERROR] Closing DB connection failed:", e)