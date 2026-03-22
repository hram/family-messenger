# План редизайна UI — Family Messenger

## Цель

Заменить текущий утилитарный UI на Telegram-подобный дизайн (из `promts/App.kt` и `promts/family_messenger_prototype.html`) с поддержкой адаптивного лейаута: на мобильных — последовательная навигация, на десктопе/вебе — split-pane.

## Исходное состояние

- **Текущий файл:** `client/composeApp/src/commonMain/kotlin/app/App.kt` — старый дизайн (градиент, Material3 дефолты, отладочная информация на экранах)
- **Готовый редизайн:** `promts/App.kt` — Telegram-стиль, но только мобильная навигация
- **AppState.kt** — не требует изменений, вся нужная структура уже есть

---

## Шаг 1 — Заменить App.kt на версию из promts/

**Файл:** `client/composeApp/src/commonMain/kotlin/app/App.kt`

Взять `promts/App.kt` как основу. Он уже содержит:
- Telegram-палитру (`TgBlue`, `AppBg`, `BubbleMe` и т.д.)
- `TgTopBar`, `AvatarCircle`, `TgChip`, `TgTextField`, `TgToggleRow`, `TgInfoRow`
- `MessageBubble` с хвостом и иконками статуса (✓✓)
- `ContactRow` с аватаром и online-dot
- `SettingsScreen` в стиле iOS-секций
- Убрана вся отладочная информация (syncCursor, uuid, архитектурные описания)

**Что нужно проверить/поправить при копировании:**
- `MessageStatus.prettyLabel()` — уже есть в обоих файлах, убедиться нет дублирования
- Импорты — `promts/App.kt` использует `IconButton`, `HorizontalDivider`, `CircleShape` — добавить если не хватает
- `state.availableQuickActions()` — функция уже есть в `AppState.kt`, всё ок

---

## Шаг 2 — Разбить экраны на переиспользуемые панели

**Цель:** чтобы десктоп/веб мог показывать ContactsPanel и ChatPanel рядом.

### 2.1 Переименовать внутренние Composable

Текущие `ContactsScreen` / `ChatScreen` — это полноэкранные функции. Нужно извлечь контент в отдельные панели:

```
ContactsScreen(...)     →  оставить как оболочку
  ContactsPanel(...)    →  новая: только список контактов без топбара

ChatScreen(...)         →  оставить как оболочку (мобиль: с TopBar и кнопкой назад)
  ChatPanel(...)        →  новая: только область сообщений + ввод + квик-экшены

SettingsScreen(...)     →  оставить как оболочку
  SettingsPanel(...)    →  новая: только контент настроек
```

### 2.2 Подписи панелей

```kotlin
// Панель списка контактов (без шапки — шапка снаружи)
@Composable
fun ContactsPanel(
    contacts: List<ContactSummary>,
    currentUser: UserProfile?,
    selectedContactId: Long? = null,   // нужен для подсветки активного (Шаг 4)
    onContactClick: (ContactSummary) -> Unit,
)

// Панель чата (без шапки — шапка снаружи)
@Composable
fun ChatPanel(
    state: AppUiState,
    viewModel: AppViewModel,
)

// Панель настроек (без шапки)
@Composable
fun SettingsPanel(
    state: AppUiState,
    viewModel: AppViewModel,
)
```

---

## Шаг 3 — Адаптивный корневой лейаут

### 3.1 Определить breakpoint

Использовать `BoxWithConstraints` — работает на всех таргетах (Android, iOS, Desktop, WASM) без дополнительных зависимостей. `LocalWindowInfo` ненадёжен на части таргетов.

```kotlin
// В FamilyMessengerApp вместо Scaffold/Box:
BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWide = maxWidth > 600.dp
    // далее передавать isWide вниз
}
```

### 3.2 Логика в `FamilyMessengerApp`

```kotlin
when {
    state.screen == Screen.ONBOARDING -> OnboardingScreen(...)

    isWide && state.screen in listOf(Screen.CONTACTS, Screen.CHAT, Screen.SETTINGS) -> {
        WideLayout(state, viewModel)   // split-pane
    }

    else -> {
        // мобильная навигация как сейчас
        when (state.screen) {
            Screen.CONTACTS -> ContactsScreen(...)
            Screen.CHAT     -> ChatScreen(...)
            Screen.SETTINGS -> SettingsScreen(...)
            else -> {}
        }
    }
}
```

### 3.3 `WideLayout` — split-pane для десктопа/веба

```kotlin
@Composable
fun WideLayout(state: AppUiState, viewModel: AppViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {

        // Левая колонка — список контактов (фиксированная ширина ~280dp)
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color.White),
        ) {
            TgTopBar(
                title = "Chats",
                subtitle = state.currentUser?.displayName ?: "",
                trailingContent = { /* ⚙ и ↻ */ }
            )
            ContactsPanel(
                contacts = state.contacts,
                currentUser = state.currentUser,
                selectedContactId = state.selectedContactId,   // AppState.kt:44, тип Long?
                onContactClick = viewModel::openChat,
            )
        }

        // Вертикальный разделитель
        // CMP 1.7.1 бандлит Material3 ~1.3.x → VerticalDivider доступен.
        // Если вдруг не скомпилируется: заменить на
        //   Box(Modifier.width(0.5.dp).fillMaxHeight().background(Divider))
        VerticalDivider(color = Divider, thickness = 0.5.dp)

        // Правая колонка — чат или заглушка
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (state.selectedContactId != null) {
                Column {
                    TgTopBar(title = state.selectedContactName ?: "")
                    ChatPanel(state = state, viewModel = viewModel)
                }
            } else {
                // Заглушка пока контакт не выбран
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", fontSize = 48.sp)
                        Text("Выбери контакт", color = TextSecondary)
                    }
                }
            }

            // Настройки — оверлей поверх правой колонки: затемнение + панель справа
            if (state.screen == Screen.SETTINGS) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(onClick = viewModel::backToContacts)  // клик по затемнению закрывает
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(360.dp)
                        .fillMaxHeight(),
                    color = AppBg,
                ) {
                    SettingsPanel(state, viewModel)
                }
            }
        }
    }
}
```

### 3.4 Настройки в широком лейауте

Два варианта — выбрать один:

**А) Оверлей справа** — `SettingsPanel` в `Box` поверх правой колонки с фоном (как drawer)

**Б) Третья колонка** — добавить ещё одну панель справа (только если экран очень широкий, > 900dp)

Рекомендую **А** — проще и достаточно для текущего MVP.

---

## Шаг 4 — Подсветка активного контакта в широком лейауте

В `ContactsPanel` нужно передавать `selectedContactId` и подсвечивать строку:

```kotlin
val isSelected = contact.user.id == selectedContactId
Row(
    modifier = Modifier
        .background(if (isSelected) TgBlueTint else Color.Transparent)
        ...
)
```

---

## Шаг 5 — Убрать кнопку "Назад" в широком лейауте

В `ChatScreen` (мобильный) кнопка "‹" нужна. В `WideLayout` — нет, там контакт выбирается кликом.

`ChatPanel` не должен знать про "Назад". Кнопку рендерит только мобильный `ChatScreen`.

---

## Шаг 6 — Проверка по платформам

| Платформа | Ожидаемое поведение |
|-----------|---------------------|
| Android   | Мобильная навигация (Contacts → Chat → Settings) |
| iOS       | То же |
| Desktop   | Wide split-pane автоматически (окно > 600dp) |
| Web/JS    | Wide split-pane в браузере, мобильная если узкое окно |

Тестировать: `:client:composeApp:desktopRun` и `:client:composeApp:compileKotlinJs`

---

## Порядок выполнения

1. **Заменить App.kt** содержимым `promts/App.kt` — это даёт новый дизайн на всех платформах сразу
2. **Извлечь панели** (`ContactsPanel`, `ChatPanel`, `SettingsPanel`) из экранов
3. **Добавить `WideLayout`** с split-pane логикой
4. **Подключить `isWideLayout()`** в `FamilyMessengerApp`
5. **Добавить подсветку** активного контакта
6. **Проверить** десктоп и веб

---

## Файлы которые меняются

| Файл | Что меняется |
|------|-------------|
| `client/composeApp/src/commonMain/kotlin/app/App.kt` | Полная замена — новый дизайн + адаптивный лейаут |
| `client/composeApp/src/commonMain/kotlin/app/AppState.kt` | Не меняется |
| `client/composeApp/src/commonMain/kotlin/app/AppViewModel.kt` | Не меняется |
| `client/composeApp/build.gradle.kts` | Возможно: добавить `material3-adaptive` если используем его |

---

## Зависимости (если нужны)

Для `BoxWithConstraints` — уже есть в Compose Foundation, ничего добавлять не нужно.

Для `WindowSizeClass` — нужна `androidx.compose.material3:material3-window-size-class`. Опционально, `BoxWithConstraints` достаточно.

Для `VerticalDivider` — появился в Material3 1.2+, проверить версию в `libs.versions.toml`.
