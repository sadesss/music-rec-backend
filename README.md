# Music Recommendation Backend (Spring Boot + offline Python)

Это скелет бэкенда для музыкальной рекомендательной системы:
- Java 17 + Spring Boot 3.x
- PostgreSQL (на стационарном ПК)
- mp3 хранится на файловой системе
- Python запускается офлайн (анализ аудио и обучение модели) через ProcessBuilder

## Предпосылки
- Java 17+
- Maven 3.9+
- Python 3.10+ (или ваш)
- PostgreSQL 14+ (как минимум; рекомендую актуальную версию)

## Настройка БД
Создайте базу и пользователя (пример):

```sql
CREATE DATABASE musicrec;
CREATE USER musicrec WITH PASSWORD 'change_me';
GRANT ALL PRIVILEGES ON DATABASE musicrec TO musicrec;
