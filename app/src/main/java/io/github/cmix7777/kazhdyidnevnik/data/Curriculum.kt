package io.github.cmix7777.kazhdyidnevnik.data

import java.time.DayOfWeek
import java.time.LocalDate

data class Link(val title: String, val url: String)

data class Topic(
    val number: Int,
    val title: String,
    val summary: String,
    val practice: String,
    val links: List<Link> = emptyList(),
)

/** Тема на конкретный день: сама тема и признак повторения (после окончания программы). */
data class TopicOfDay(val topic: Topic, val repeat: Boolean)

/**
 * Учебная программа: тестирование с нуля и Laravel к демоэкзамену.
 * Неделя 1 начинается 12 октября 2026, каждая следующая неделя — следующая тема.
 */
object Curriculum {

    val startMonday: LocalDate = LocalDate.of(2026, 10, 12)

    fun weekNumber(date: LocalDate): Int {
        val days = date.toEpochDay() - startMonday.toEpochDay()
        return if (days < 0) 1 else (days / 7).toInt() + 1
    }

    private fun laravelDoc(page: String, title: String) = Link(title, "https://laravel.com/docs/11.x/$page")

    val qa: List<Topic> = listOf(
        Topic(
            1, "Основы тестирования",
            "Что такое тестирование, чем QA отличается от QC, жизненный цикл ПО и роль тестировщика.",
            "Опиши своими словами, чем QA отличается от QC, и распиши жизненный цикл ПО на примере BookClub.",
        ),
        Topic(
            2, "Виды и уровни тестирования",
            "Функциональное и нефункциональное, модульное, интеграционное, системное, приёмочное; smoke и регрессия.",
            "Для BookClub придумай по одной проверке на каждый уровень тестирования.",
        ),
        Topic(
            3, "Тест-дизайн: классы эквивалентности и граничные значения",
            "Как выбрать минимум проверок и не пропустить ошибки на границах.",
            "Поле «возраст от 14 до 99»: выпиши классы эквивалентности, граничные значения и проверки.",
        ),
        Topic(
            4, "Тест-дизайн: таблицы решений и переходы состояний",
            "Таблицы решений, диаграммы состояний, попарное тестирование.",
            "Нарисуй переходы статусов заявки «Новая → Подтверждена / Отклонена» и составь проверки на каждый переход.",
        ),
        Topic(
            5, "Чек-листы и тест-кейсы",
            "Как писать чек-листы и тест-кейсы: шаги, ожидаемый результат, предусловия.",
            "Напиши чек-лист и 10 тест-кейсов на регистрацию и вход в BookClub.",
        ),
        Topic(
            6, "Баг-репорты",
            "Структура баг-репорта, серьёзность и приоритет, жизненный цикл бага.",
            "Найди 3 бага в BookClub или на любом сайте и оформи баг-репорты.",
        ),
        Topic(
            7, "Клиент-сервер, HTTP и DevTools",
            "Запросы и ответы, методы, коды ответов, заголовки, вкладка Network.",
            "Открой DevTools на любом сайте, найди запросы с кодами 200, 301 и 404 и разбери их заголовки.",
            listOf(
                Link("HTTP: обзор (MDN)", "https://developer.mozilla.org/ru/docs/Web/HTTP/Overview"),
                Link("Chrome DevTools", "https://developer.chrome.com/docs/devtools"),
            ),
        ),
        Topic(
            8, "REST API, JSON и Postman",
            "Как устроен REST API, формат JSON, проверка запросов в Postman.",
            "Проверь в Postman 5 запросов к API BookClub из Swagger: успешные и с ошибками.",
            listOf(Link("Postman: обучение", "https://learning.postman.com/docs/introduction/overview/")),
        ),
        Topic(
            9, "SQL для тестировщика",
            "SELECT, WHERE, ORDER BY, JOIN — чтобы проверять данные в базе.",
            "Реши 10 задач на SQL и проверь запросом, что отзывы в BookClub сохраняются в базе.",
            listOf(Link("SQL Academy", "https://sql-academy.org")),
        ),
        Topic(
            10, "Тестирование веб-интерфейса",
            "Формы, вёрстка, адаптивность, кроссбраузерность.",
            "Проверь сайт на ширине 1920, 1024 и 390 px в DevTools и оформи найденные проблемы.",
        ),
        Topic(
            11, "Git, баг-трекеры и Agile",
            "Ветки и коммиты, доска задач, Scrum и канбан.",
            "Заведи доску задач (например, GitHub Projects) и перенеси туда свои баги.",
            listOf(Link("Книга Pro Git на русском", "https://git-scm.com/book/ru/v2")),
        ),
        Topic(
            12, "Введение в автотесты",
            "Зачем нужны автотесты, PHPUnit и Pest в Laravel.",
            "Напиши 3 простых теста в Laravel: страница открывается, регистрация, ошибка валидации.",
            listOf(laravelDoc("testing", "Laravel: тестирование")),
        ),
    )

    val laravel: List<Topic> = listOf(
        Topic(
            1, "Установка и структура проекта",
            "composer create-project, artisan, файл .env, подключение MySQL.",
            "Создай новый проект, подключи MySQL и запусти php artisan serve.",
            listOf(laravelDoc("installation", "Установка"), laravelDoc("configuration", "Настройка .env")),
        ),
        Topic(
            2, "Маршруты, контроллеры и Blade",
            "routes/web.php, контроллеры, шаблоны Blade и общий layout.",
            "Сделай 3 страницы с общим layout: главная, о нас, контакты.",
            listOf(laravelDoc("routing", "Маршруты"), laravelDoc("controllers", "Контроллеры"), laravelDoc("blade", "Blade")),
        ),
        Topic(
            3, "Миграции, модели и Eloquent",
            "Миграции, модели, выборки через Eloquent, сидеры.",
            "Создай таблицы users и applications миграциями и заполни их сидером.",
            listOf(laravelDoc("migrations", "Миграции"), laravelDoc("eloquent", "Eloquent"), laravelDoc("seeding", "Сидеры")),
        ),
        Topic(
            4, "Breeze: регистрация, вход и роли",
            "Установка Breeze, страницы входа и регистрации, middleware, роль администратора.",
            "Поставь Breeze, добавь поле «роль» и закрой админку от обычных пользователей.",
            listOf(laravelDoc("starter-kits", "Breeze"), laravelDoc("middleware", "Middleware")),
        ),
        Topic(
            5, "CRUD, формы и валидация",
            "Создание, просмотр, изменение и удаление записей, правила валидации, вывод ошибок.",
            "Сделай форму заявки с валидацией: дата не в прошлом, телефон по маске.",
            listOf(laravelDoc("validation", "Валидация")),
        ),
        Topic(
            6, "Связи Eloquent и пагинация",
            "hasMany и belongsTo, выборки со связями, пагинация.",
            "Выведи заявки пользователя с названием услуги и пагинацией по 5.",
            listOf(laravelDoc("eloquent-relationships", "Связи"), laravelDoc("pagination", "Пагинация")),
        ),
        Topic(
            7, "Bootstrap: сетка, адаптивность, слайдер",
            "Сетка, адаптивная вёрстка под 1920, 1024 и 390 px, компоненты, карусель.",
            "Сверстай главную со слайдером и проверь её на трёх ширинах.",
            listOf(
                Link("Bootstrap: сетка", "https://getbootstrap.com/docs/5.3/layout/grid/"),
                Link("Bootstrap: карусель", "https://getbootstrap.com/docs/5.3/components/carousel/"),
            ),
        ),
        Topic(
            8, "Админ-панель",
            "Список всех заявок, смена статусов, фильтры.",
            "Сделай админку: все заявки, фильтр по статусу, смена статуса кнопкой.",
            listOf(laravelDoc("queries", "Запросы к базе")),
        ),
        Topic(
            9, "Загрузка файлов",
            "Загрузка картинок, хранилище storage, проверка типа и размера файла.",
            "Добавь к заявке необязательное фото jpg или png до 2 МБ.",
            listOf(laravelDoc("filesystem", "Файлы"), laravelDoc("requests", "Запросы и файлы")),
        ),
        Topic(
            10, "Безопасность",
            "CSRF, XSS, SQL-инъекции, политики доступа.",
            "Проверь свои формы: есть ли @csrf, экранируется ли вывод, закрыта ли админка.",
            listOf(laravelDoc("csrf", "CSRF"), laravelDoc("authorization", "Политики доступа")),
        ),
        Topic(
            11, "Zeal и скорость",
            "Офлайн-документация Zeal, заготовки, чек-лист экзамена.",
            "Найди в Zeal ответы на 5 вопросов без интернета и составь свой чек-лист на экзамен.",
            listOf(Link("Zeal", "https://zealdocs.org")),
        ),
    )

    fun qaTopic(date: LocalDate): TopicOfDay = topicFor(qa, date)

    fun laravelTopic(date: LocalDate): TopicOfDay = topicFor(laravel, date)

    private fun topicFor(topics: List<Topic>, date: LocalDate): TopicOfDay {
        val week = weekNumber(date)
        return if (week <= topics.size) {
            TopicOfDay(topics[week - 1], repeat = false)
        } else {
            TopicOfDay(topics[(week - topics.size - 1) % topics.size], repeat = true)
        }
    }

    /** Что делать с темой в этот день недели. */
    fun step(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Теория: прочитай и законспектируй главное"
        DayOfWeek.TUESDAY -> "Теория: разбери примеры"
        DayOfWeek.WEDNESDAY -> "Практика: повтори на своём примере"
        DayOfWeek.THURSDAY -> "Практика: сделай упражнение"
        DayOfWeek.FRIDAY -> "Практика: мини-задание"
        DayOfWeek.SATURDAY -> "Повторение: перескажи тему своими словами"
        DayOfWeek.SUNDAY -> "Итог недели: проверь себя по вопросам"
    }

    /** Подготовка к сдаче сайта на C# — до 31 октября, по пн, ср, пт и вс. */
    val csharpDeadline: LocalDate = LocalDate.of(2026, 10, 31)

    private val csharpTasks = listOf(
        "Прогони сайт целиком и запиши сценарий показа по шагам",
        "Расскажи вслух архитектуру: контроллеры, EF Core, миграции, роли",
        "Подготовь ответы на вероятные вопросы преподавателя",
        "Проверь, что сайт открывается по ссылке, покажи админку и рецензии",
    )

    fun csharpDue(date: LocalDate): Boolean =
        !date.isAfter(csharpDeadline) &&
            date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)

    fun csharpTask(date: LocalDate): String = csharpTasks[(date.dayOfYear / 2) % csharpTasks.size]
}
