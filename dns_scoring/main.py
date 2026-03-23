import pymysql
import scorer
def collect():
    try:
        connection = pymysql.connect(
            host='localhost',
            user='root',
            password='',
            db='nsm',
        )
        with connection.cursor() as cursor:
            cursor.execute('select query_name from dns where query_name is not null')
            res = cursor.fetchall()
            scorer.Scorer(res)
    except Exception as e:
        print("[ERROR]", e)
        return False

if __name__ == '__main__':
    collect()
