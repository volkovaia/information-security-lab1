# Лабораторная работа №1: Разработка защищенного REST API с интеграцией в CI/CD

## 1. Описание проекта

Проект представляет собой безопасный веб-сервис (REST API), спроектированный с учетом требований стандарта OWASP Top 10. В проект интегрирован автоматизированный процесс непрерывной интеграции (CI/CD) на базе GitHub Actions, включающий статическое тестирование безопасности исходного кода (SAST) и анализ используемых зависимостей на наличие известных уязвимостей (SCA).

## 2. Спецификация REST API

В приложении реализована модель доступа на базе JWT: общедоступным является только эндпоинт входа, а доступ к ресурсам данных требует наличия валидного токена авторизации.

### 1) Аутентификация пользователя

- **URL:** `POST /auth/login`
- **Доступ:** публичный (не требует токена)
- **Назначение:** проверка учетных данных пользователя и выдача JWT-токена доступа
- **Тело запроса (JSON):**

  ```json
  {
    "username": "admin",
    "password": "password123"
  }
  ```

- **Успешный ответ (200 OK):**

  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZ6MTcz..."
  }
  ```

- **Ответ при неверных данных (401 Unauthorized):**

  ```json
  {
    "error": "Invalid credentials"
  }
  ```

### 2) Получение списка данных

- **URL:** `GET /api/data`
- **Доступ:** защищенный (требуется JWT-токен в заголовке `Authorization: Bearer <token>`)
- **Назначение:** получение списка всех сохраненных записей из базы данных.
- **Заголовки запроса:**
  - `Authorization: Bearer <ваш_jwt_токен>`
- **Ответ при отсутствии/невалидности токена (403 Forbidden):** доступ отклоняется middleware-фильтром
- **Успешный ответ (200 OK):**

  ```json
  [
    {
      "id": 1,
      "title": "Опасный заголовок ",
      "content": "Обычный текст"
    }
  ]
  ```

### 3) Создание новой записи с санитизацией

- **URL:** `POST /api/data`
- **Доступ:** защищенный (требуется JWT-токен)
- **Назначение:** добавление новой записи с автоматической фильтрацией вредоносного содержимого
- **Заголовки запроса:**
  - `Authorization: Bearer <ваш_jwt_токен>`
  - `Content-Type: application/json`
- **Тело запроса (пример с попыткой XSS-атаки):**

  ```json
  {
    "title": "Опасный заголовок <script>alert('xss')</script>",
    "content": "Обычный текст"
  }
  ```

- **Успешный ответ (200 OK):**

  ```json
  {
    "id": 1,
    "title": "Опасный заголовок ",
    "content": "Обычный текст"
  }
  ```

(Тег `<script>` вырезан санитайзером перед сохранением в БД).

## 3. Подробное описание реализованных мер защиты

### 1) Защита от SQL-инъекций (SQL Injection / CWE-89)

- Использование Spring Data JPA на базе Hibernate ORM:
  
  - доступ к данным инкапсулирован в интерфейсах `UserRepository` и `NoteRepository`
  - приложение не использует динамическую конкатенацию строк для формирования SQL-запросов 
  - Hibernate транслирует методы репозиториев (`findByUsername`, `findAll`, `save`) в параметризованные запросы с плейсхолдерами `?`

### 2) Защита от межсайтового скриптинга (Cross-Site Scripting / XSS / CWE-79)

- Входная санитизация данных на уровне бэкенда с помощью библиотеки OWASP Java HTML Sanitizer
  - в классе `DataController` используется санитизация:

    ```java
    private final PolicyFactory sanitizer = new HtmlPolicyBuilder().toFactory();
    ```

  - перед сохранением полей сущности `Note` в базу данных все строковые поля проходят через метод `.sanitize()`:

    ```java
    String cleanTitle = sanitizer.sanitize(request.title());
    String cleanContent = sanitizer.sanitize(request.content());
    ```

  - опасные HTML/JS-теги (например, `<script>`, `<iframe>`, события `onload`, `onerror`) удаляются или экранируются до того, как попадают в базу данных или возвращаются в ответе клиенту 

### 3) Защита от скомпрометированной аутентификации

- **Безопасное хранение паролей:**
  - пароли не хранятся в открытом виде
  - используется алгоритм BCrypt (`BCryptPasswordEncoder`), включающий криптографическую соль 
  - сверка пароля при логине производится методом `passwordEncoder.matches(raw, hash)` с константным временем выполнения для защиты от Timing Attacks
- **Аутентификация по протоколу JWT:**
  - при успешном входе генерируется криптографически подписанный токен алгоритмом HMAC-SHA256 (`Keys.hmacShaKeyFor`) со сроком жизни 1 час (`EXPIRATION_TIME = 3600s`)
  - архитектура API настроена как Stateless (`SessionCreationPolicy.STATELESS`)
- **Middleware-валидация (Security Filter):**
  - реализован фильтр `JwtFilter`
  - фильтр перехватывает все входящие запросы, извлекает заголовок `Authorization: Bearer <token>`, проверяет токен и срок его годности
  - при попытке неавторизованного доступа к `/api/**` сервер возвращает статус 403 Forbidden

## 4. Настройка CI/CD Pipeline с security-сканерами

В корне репозитория настроен файл конфигурации GitHub Actions `.github/workflows/ci.yml`. Процесс запускается автоматически при каждом событии `push` и `pull_request` в ветки `master` и `main`

В пайплайн интегрированы:

1. **Static Application Security Testing:**
   - SpotBugs с плагином FindSecBugs производит статический анализ скомпилированного байткода Java на наличие CWE, некорректных криптографических настроек, потенциальных утечек ресурсов и логических ошибок
2. **Software Composition Analysis:**
   - OWASP Dependency-Check сканирует все сторонние библиотеки, объявленные в `pom.xml`, сопоставляя их с базой уязвимостей NVD на наличие известных CVE
3. **Artifacts Collection:**
   - отчеты проверок (`spotbugsXml.xml` и `dependency-check-report.html`) автоматически архивируются и публикуются в разделе Artifacts каждого запуска пайплайна

## 5. Результаты тестирования и отчеты

### 1) Тестирование безопасности API в Postman

| Запрос | Результат |
| --- | --- |
| GET /api/data (без токена) | Блокировка неавторизованного запроса: 403 Forbidden |
| POST /auth/login | Аутентификация по BCrypt-хэшу и выдача JWT: 200 OK, получен токен |
| POST /api/data (с пейлоадом XSS) | Очистка тегов `<script>` санитайзером OWASP: 200 OK, тег обезврежен |
| GET /api/data (с токеном) | Чтение очищенных данных из защищенной БД: 200 OK, данные получены |

<img width="727" height="456" alt="image" src="https://github.com/user-attachments/assets/7ae14dbb-2b7f-4763-a6b4-b73fa68a7704" />
<img width="731" height="457" alt="image" src="https://github.com/user-attachments/assets/eb90529e-a538-4c00-8afa-234fce25dcee" />
<img width="734" height="455" alt="image" src="https://github.com/user-attachments/assets/5a8b54f5-6d46-48ef-be7b-4e8f90ad4dc0" />
<img width="730" height="456" alt="image" src="https://github.com/user-attachments/assets/de922d3d-d788-47c3-bb58-2abdf3bbef6f" />


### 2) Отчеты SAST/SCA из раздела "Actions" репозитория

Успешное выполнение Pipeline (GitHub Actions):

<img width="1280" height="526" alt="image" src="https://github.com/user-attachments/assets/09335ae7-079f-4ea9-936c-a817d8e9c11a" />


- **Результаты анализа зависимостей (SCA: OWASP Dependency-Check):**


<img width="1836" height="768" alt="image" src="https://github.com/user-attachments/assets/ac32f139-03e3-4443-af38-88651c01041b" />
Проверка зависимостей сторонних библиотек и плагинов на наличие зарегистрированных CVE прошла успешно. Критических уязвимостей в используемых компонентах не обнаружено. 

- **Результаты статического анализа кода (SAST: SpotBugs)**

- Были найдены и исправлены 2 уязвимости: 


SS_SHOULD_BE_STATIC (Категория: Performance) — константа EXPIRATION_TIME не объявлена как static.
```html
<BugPattern abbrev="SS" category="PERFORMANCE" type="SS_SHOULD_BE_STATIC">
<ShortDescription>Unread field: should this field be static?</ShortDescription>
<Details> <p> This class contains an instance final field that is initialized to a compile-time static value. Consider making the field static.</p> </Details>
</BugPattern>
```

CT_CONSTRUCTOR_THROW (Категория: Bad Practice) — конструктор может выбросить исключение при генерации ключа

```html
<BugPattern abbrev="CT" category="BAD_PRACTICE" type="CT_CONSTRUCTOR_THROW">
<ShortDescription>Be wary of letting constructors throw exceptions.</ShortDescription>
<Details> <p>Classes that throw exceptions in their constructors are vulnerable to Finalizer attacks</p> <p>A finalizer attack can be prevented, by declaring the class final, using an empty finalizer declared as final, or by a clever use of a private constructor.</p> <p>See <a href="https://wiki.sei.cmu.edu/confluence/display/java/OBJ11-J.+Be+wary+of+letting+constructors+throw+exceptions"><code>SEI CERT Rule OBJ-11</code></a> for more information. </p> </Details>
</BugPattern>
```

