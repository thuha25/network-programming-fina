import os
from collections import OrderedDict

import pyexcel

CLIENT_CACHE_DIR = "./test_case"
OUT_EXAMINATOR_CSV = "./convert/_supervisors.csv"
OUT_ROOM_CSV = "./convert/_rooms.csv"

if not os.path.exists(CLIENT_CACHE_DIR):
    os.makedirs(CLIENT_CACHE_DIR)


def convert_examinator(xlsx_file):
    records = pyexcel.get_records(file_name=xlsx_file)
    csv_records = []
    for record in records:
        csv_record: OrderedDict = OrderedDict()
        csv_record["Mã cán bộ"] = record["Mã cán bộ"]
        csv_record["Họ và tên"] = record["Họ và tên"]
        csv_record["Ngày sinh"] = record["Ngày sinh"]
        csv_record["Đơn vị công tác"] = record["Đơn vị công tác"]
        csv_records.append(csv_record)

    pyexcel.save_as(records=csv_records, dest_file_name=OUT_EXAMINATOR_CSV)


def convert_room(xlsx_file):
    records = pyexcel.get_records(file_name=xlsx_file)
    csv_records = []
    for record in records:
        csv_record: OrderedDict = OrderedDict()
        csv_record["Phòng thi"] = record["Phòng thi"]
        csv_record["Địa điểm"] = record["Địa điểm"]
        csv_records.append(csv_record)

    pyexcel.save_as(records=csv_records, dest_file_name=OUT_ROOM_CSV)


if __name__ == "__main__":
    convert_examinator("./test_case/can_bo_thi.xlsx")
    convert_room("./test_case/phong_thi.xlsx")
