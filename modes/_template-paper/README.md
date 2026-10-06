# Khuôn mẫu chế độ Paper

Không chạy trực tiếp thư mục này. Tạo chế độ mới bằng:

```bash
./ops/new-mode.sh <id>      # ví dụ: ./ops/new-mode.sh survival
```

Script sẽ copy khuôn mẫu sang `modes/<id>/`, thay tên và thêm dòng `include` vào `compose.yaml` gốc.
Các bước còn lại script sẽ in ra (khai báo server trong Velocity, tạo database).
