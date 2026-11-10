# XOR scramble, TOTP и сборка

Форк основан на [schwabe/ics-openvpn](https://github.com/schwabe/ics-openvpn) `660901c`.
OpenVPN 2.8-dev (`schwabe/openvpn` `c29ee42` + XOR-патч) лежит прямо в репозитории,
в `main/src/main/cpp/openvpn`, а не подмодулем.

Отличия от upstream:

- XOR-обфускация `scramble` — порт патча Tunnelblick / [OpenVPN-XOR-windows](https://github.com/samanajd2/OpenVPN-XOR-windows);
- встроенный генератор TOTP-кодов (RFC 6238);
- ядро OpenVPN 3 удалено целиком (флейворы `ovpn23`/`ovpn2`, SWIG, подмодули `openvpn3`, `asio`, `fmt`).

## XOR (scramble)

Синтаксис и поведение на проводе совпадают с Tunnelblick и OpenVPN-XOR-windows,
поэтому сервер должен быть собран с тем же XOR-патчем.

```
scramble xormask <ключ>
scramble xorptrpos
scramble reverse
scramble obfuscate <ключ>
```

- Работает и по UDP, и по TCP; можно задавать внутри `<connection>`.
- При импорте `.ovpn` опция переносится в настройки соединения автоматически.
- Включённый `scramble` отключает DCO.

## TOTP

**Настройки профиля → Основное**, рядом с логином и паролем: «Генерировать одноразовые TOTP-коды (2FA)».

- **Секрет:** Base32 или целиком ссылка `otpauth://totp/...` из QR-кода. Параметры из ссылки имеют приоритет.
- **Параметры:** цифры (6–10), период (по умолчанию 30 с), алгоритм SHA1, SHA256 или SHA512.
- **Превью:** под полями показывается текущий код и сколько секунд он ещё действует.

Режимы:

1. **Отвечать на запрос сервера** (по умолчанию):
   - если в конфиге есть `static-challenge`, код отправляется по SCRV1, а для формата `concat` дописывается к паролю;
   - на динамический запрос `CR_TEXT` приложение отвечает автоматически, без диалога.
2. **Добавлять код к паролю** — всегда `пароль+код`. Подходит для серверов (RADIUS/LDAP+OTP), которые ждут их слитно.

Секрет хранится в профиле так же, как сохранённый пароль.

## Сборка через GitHub Actions

Каждый push собирает APK. Ещё сборку можно запустить вручную: **Actions → Build → Run workflow**.
Готовый файл — в артефакте `build-Ui-release-apk` (`main-ui-universal-release.apk`).
Он подписан отладочным ключом, поэтому устанавливается сразу.

## Сборка на Debian 13

Нужно около 15 ГБ на диске и от 8 ГБ ОЗУ.

```sh
# 1. Пакеты
sudo apt update
sudo apt install -y openjdk-21-jdk git unzip curl

# 2. Android command-line tools (актуальная ссылка: developer.android.com/studio → Command line tools only)
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q commandlinetools-linux-*_latest.zip && mv cmdline-tools latest && rm commandlinetools-linux-*.zip
cat >> ~/.bashrc <<'EOF'
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
EOF
source ~/.bashrc

# 3. Компоненты SDK (версия NDK задана в main/build.gradle.kts)
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37" "ndk;30.0.14904198" "cmake;3.22.1"

# 4. Исходники
cd ~
git clone --recurse-submodules https://github.com/andruxaster/ics-openvpn-xor
cd ics-openvpn-xor
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 5. Сборка (первая — 15–30 минут)
./gradlew assembleUiRelease -PicsopenvpnDebugSign
```

Готовый APK: `main/build/outputs/apk/ui/release/main-ui-universal-release.apk`.

Установка: `adb install -r <apk>` или просто скопировать файл на телефон.
Официальный OpenVPN for Android из Google Play сначала нужно удалить: имя пакета то же, а подпись другая.

### Если что-то пошло не так

- **`NDK not configured`:** не та версия NDK, повторите шаг 3.
- **`SDK location not found`:** нет `local.properties`.
- **Gradle падает по памяти:**
  - добавьте `org.gradle.jvmargs=-Xmx4g` в `~/.gradle/gradle.properties`;
  - запускайте с `--max-workers=2`.
- **Подмодули не скачались:** `git submodule update --init --recursive`.

### Подпись своим ключом

```sh
keytool -genkeypair -v -keystore ~/ics.jks -alias ics -keyalg RSA -keysize 4096 -validity 10000
```

Пропишите ключ в `~/.gradle/gradle.properties`:

```
keystoreFile=/home/<user>/ics.jks
keystorePassword=...
keystoreAlias=ics
keystoreAliasPassword=...
```

Затем собирайте без `-PicsopenvpnDebugSign`.

## Что проверено

- XOR: нативная часть собрана под Linux, все 4 режима соединяются по UDP и TCP; неверный ключ ожидаемо не соединяется.
- TOTP: тестовые векторы RFC 6238 (`TestTotp`).
- Импорт `scramble`: `TestConfigParser.testScrambleImport`.
