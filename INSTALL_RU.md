# Как получить готовый jar без установки чего-либо на ПК

1. Зарегистрируйтесь на github.com (бесплатно) и создайте новый репозиторий (New repository).
2. Нажмите "uploading an existing file" и перетащите ВСЁ содержимое этого архива
   (папки `src`, `.github` и файлы `build.gradle`, `settings.gradle`, `gradle.properties`).
   Если папка `.github` не загрузилась (скрытая папка): Add file -> Create new file,
   имя `.github/workflows/build.yml`, и вставьте содержимое файла из архива.
3. Откройте вкладку **Actions** - сборка запустится сама (3-6 минут).
   Если не запустилась: Actions -> Build mod -> Run workflow.
4. Откройте завершённый запуск, внизу в разделе Artifacts скачайте `rustbuild-jar`,
   распакуйте - внутри `rustbuild-1.0.0.jar`.
5. Положите jar в `%appdata%\.minecraft\mods` (нужен Minecraft 1.21.1 + Forge 52.x).

Если сборка упала красным - откройте лог и пришлите мне текст ошибки, я исправлю код.
