OpenVPN для Android — XOR + TOTP
================================
![build status](https://github.com/andruxaster/ics-openvpn-xor/actions/workflows/build.yaml/badge.svg)

Форк [OpenVPN for Android](https://github.com/schwabe/ics-openvpn) (ics-openvpn) Арне Швабе с тремя дополнениями:

- **XOR-обфускация трафика** (`scramble`). Совместима с серверами и клиентами на патче Tunnelblick,
  в том числе с [OpenVPN-XOR-windows](https://github.com/samanajd2/OpenVPN-XOR-windows).
- **Двухфакторная авторизация по TOTP.** Можно вводить код вручную, как в OpenVPN GUI для Windows,
  или включить встроенный генератор кодов.
- **Только ядро OpenVPN 2.x.** Экспериментальное ядро OpenVPN 3 удалено, поэтому все опции профиля работают одинаково.

В основе — upstream `660901c` и OpenVPN 2.8-dev с XOR-патчем. Исходники OpenVPN лежат прямо в репозитории,
в `main/src/main/cpp/openvpn`.

Скачать
-------

Готовый APK собирается на каждый push:
**[Actions → Build](https://github.com/andruxaster/ics-openvpn-xor/actions/workflows/build.yaml)** → последний успешный запуск →
артефакт **`build-Ui-release-apk`**. Внутри один файл `main-ui-release.apk` для всех архитектур
(arm64-v8a, armeabi-v7a, x86, x86_64). Он подписан отладочным ключом и ставится сразу.

> Имя пакета то же, что у официального приложения (`de.blinkt.openvpn`), а подпись другая.
> Если стоит OpenVPN for Android из Google Play или F-Droid, сначала удалите его.
> Профили при этом не переносятся — импортируйте их заново.

XOR-обфускация
--------------

Опция в `.ovpn` (клиент и сервер должны использовать одинаковый метод и ключ):

```
scramble xormask <ключ>       # XOR с ключом
scramble xorptrpos            # XOR с номером байта
scramble reverse              # переворот пакета (кроме первого байта)
scramble obfuscate <ключ>     # все три вместе — самый надёжный вариант
```

- Работает по UDP и TCP; опцию можно задавать внутри блоков `<connection>`.
- При импорте `.ovpn` опция переносится в настройки соединения автоматически.
- Включённый `scramble` отключает DCO (ускорение в ядре), это нормально.
- Сервер должен быть собран с тем же XOR-патчем. С обычным OpenVPN-сервером соединиться не получится.

TOTP (двухфакторная авторизация)
--------------------------------

### Ввод кода вручную

Если в конфиге есть `static-challenge`, например:

```
auth-user-pass
static-challenge "TOTP" 1
```

то при каждом подключении появится окно с логином, паролем, текстом запроса (`TOTP`) и полем **«Ответный код»**.
Если логин и пароль сохранены, курсор сразу встаёт в поле кода. Флаг `1` в конце строки делает вводимый код видимым.

Код отправляется серверу в формате SCRV1, как это делает OpenVPN GUI для Windows (поддерживается и формат `concat`).
При обрыве связи сервер снова запросит код: переподключитесь и введите новый.

### Встроенный генератор

**Настройки профиля → Основное → «Генерировать одноразовые TOTP-коды (2FA)»**.

- **Секрет** — Base32 или целиком ссылка `otpauth://totp/...` из QR-кода (параметры из ссылки имеют приоритет).
- **Параметры** — число цифр (6–10), период (по умолчанию 30 с), алгоритм SHA1 / SHA256 / SHA512.
- **Превью** — под полями виден текущий код и сколько секунд он ещё действует; удобно сверить с аутентификатором.
- **Режим «Отвечать на запрос сервера»** (по умолчанию) — код уходит в ответ на `static-challenge`, а на динамический
  запрос `CR_TEXT` приложение отвечает само, без окна.
- **Режим «Добавлять код к паролю»** — всегда отправляется `пароль+код`. Это для серверов (RADIUS/LDAP+OTP),
  которые ждут их слитно, без `static-challenge`.

С генератором окно ввода кода не появляется. Секрет хранится в профиле так же, как сохранённый пароль.

Пример профиля
--------------

```
client
dev tun
proto udp
remote vpn.example.com 443
nobind
persist-key
persist-tun

remote-cert-tls server
tls-version-min 1.2
auth SHA256
cipher AES-256-CBC

auth-user-pass
auth-retry interact
static-challenge "TOTP" 1

scramble obfuscate 12345

<ca>
...
</ca>
<cert>
...
</cert>
<key>
...
</key>
key-direction 1
<tls-auth>
...
</tls-auth>
```

Windows-специфичные опции (`block-outside-dns`, `register-dns`, `route-method`) при импорте игнорируются,
их можно не убирать из общего конфига.

Удобнее всего держать сертификаты и ключи внутри `.ovpn`, как в примере выше. Тогда профиль — один файл:
его можно переслать на телефон и импортировать из любого файлового менеджера.

Сборка
------

### Через GitHub Actions

Каждый push в `master` собирает APK и прогоняет юнит-тесты. Вручную: **Actions → Build → Run workflow**.
В своём форке этого репозитория всё работает так же, ничего настраивать не нужно.

### Локально (Debian 13 / Ubuntu)

Нужно около 15 ГБ на диске и от 8 ГБ ОЗУ.

```sh
# JDK и утилиты
sudo apt install -y openjdk-21-jdk git unzip curl

# Android SDK (актуальная ссылка: developer.android.com/studio → Command line tools only)
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q commandlinetools-linux-*_latest.zip && mv cmdline-tools latest
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37" "ndk;30.0.14904198" "cmake;3.22.1"

# Исходники и сборка (первая — 15–30 минут)
cd ~
git clone --recurse-submodules https://github.com/andruxaster/ics-openvpn-xor
cd ics-openvpn-xor
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleUiRelease -PicsopenvpnDebugSign
```

Готовый файл: `main/build/outputs/apk/ui/release/main-ui-release.apk`.

Подпись своим ключом, частые ошибки и подробности — в [XOR-TOTP.md](XOR-TOTP.md).

Управление из других приложений
-------------------------------

Как и в оригинале, есть AIDL API и простые activity для запуска и остановки VPN (например, из Tasker):

- `de.blinkt.openvpn.api.ConnectVPN`
- `de.blinkt.openvpn.api.DisconnectVPN`
- `de.blinkt.openvpn.api.PauseVPN`
- `de.blinkt.openvpn.api.ResumeVPN`

Имя профиля передаётся в extra `de.blinkt.openvpn.api.profileName`. Проверка через adb:

    adb shell am start -a android.intent.action.MAIN -n de.blinkt.openvpn/.api.ConnectVPN --es de.blinkt.openvpn.api.profileName myvpnprofile

Если у профиля есть `static-challenge`, а генератор TOTP не настроен, при таком запуске откроется окно ввода кода.

Для корпоративного управления (MDM) доступны те же настройки, что и в оригинале,
см. [app_restrictions.xml](main/src/main/res/xml/app_restrictions.xml). Настройки ядра OpenVPN 3 убраны.

Отличия от upstream
-------------------

- `scramble` — порт XOR-патча Tunnelblick на OpenVPN 2.8 (`main/src/main/cpp/openvpn`).
- Генератор TOTP (`Totp.java`, RFC 6238), ответ на `static-challenge` и `CR_TEXT`, окно ввода кода.
- Удалено ядро OpenVPN 3: флейворы `ovpn23`/`ovpn2`, SWIG, подмодули `openvpn3`, `asio`, `fmt`.
  Варианты сборки теперь просто `ui` и `skeleton`.
- Сервис VPN находит только что импортированный профиль с первого раза, даже если список профилей в его процессе устарел.
- CI: исправлен CodeQL, APK выкладывается в артефакты, сборку можно запустить вручную.

Лицензия и благодарности
------------------------

- Приложение — [OpenVPN for Android](https://github.com/schwabe/ics-openvpn), © Arne Schwabe,
  GPLv2 с дополнительными условиями, см. [doc/LICENSE.txt](doc/LICENSE.txt). Форк распространяется на тех же условиях.
- OpenVPN — GPLv2. XOR-патч — Tunnelblick / clayface, порт для Windows — [samanajd2/OpenVPN-XOR-windows](https://github.com/samanajd2/OpenVPN-XOR-windows).
- Оригинальный README, FAQ и документация разработчика:
  [github.com/schwabe/ics-openvpn](https://github.com/schwabe/ics-openvpn) и [doc/README.txt](doc/README.txt).

Это неофициальная сборка. Об ошибках в XOR, TOTP и сборке пишите в issues этого репозитория,
а не автору оригинального приложения.
