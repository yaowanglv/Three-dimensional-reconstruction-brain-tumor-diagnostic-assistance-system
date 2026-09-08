# 数据库脚本

按编号顺序在 MySQL 8 执行：

```bash
mysql -u root -p < sql/00_init.sql
mysql -u root -p < sql/01_user_auth.sql
mysql -u root -p < sql/02_detect.sql
mysql -u root -p < sql/03_config.sql
mysql -u root -p < sql/04_dataview.sql
mysql -u root -p < sql/05_views.sql
```

默认库名：`dsecond`。脚本只含表结构、默认系统配置和视图，不含业务数据和演示账号。首次登录请自行在 `admin` 表插入用户。
