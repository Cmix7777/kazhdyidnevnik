/**
 * Каждыйдневник: бесплатная пересылка уведомлений о сообщениях.
 *
 * Работает на твоём Google-аккаунте (Google Apps Script), денег не стоит. Телефон, отправивший сообщение,
 * присылает сюда токен второго телефона и зашифрованную копию сообщения, а скрипт передаёт её Firebase,
 * и тот будит второй телефон. Прочитать сообщение скрипт не может: ключ есть только у телефонов.
 *
 * Нужны два свойства скрипта (Настройки проекта → Свойства скрипта):
 *   APP_ID           номер Android-приложения в Firebase, вида 1:1234567890:android:abc123…
 *   SERVICE_ACCOUNT  всё содержимое JSON-файла ключа сервисного аккаунта Firebase
 * Как их получить и как развернуть скрипт — в push/README.md.
 */

var FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
var TOKEN_URL = 'https://oauth2.googleapis.com/token';
var MAX_PER_MINUTE = 40;

/** Телефон проверяет ссылку и узнаёт номер приложения в Firebase. */
function doGet() {
  var props = PropertiesService.getScriptProperties();
  var appId = props.getProperty('APP_ID') || '';
  var ready = /^1:\d+:android:[0-9a-f]+$/.test(appId) && !!props.getProperty('SERVICE_ACCOUNT');
  return json_({ app: 'kd-push', v: 1, ok: ready, appId: ready ? appId : '' });
}

/** Переслать пуш: { to: <токен телефона>, data: { ... } }. */
function doPost(e) {
  var request;
  try {
    request = JSON.parse(e.postData.contents);
  } catch (err) {
    return json_({ ok: false, error: 'bad_json' });
  }
  var token = String(request.to || '');
  if (!/^[A-Za-z0-9_:\-]{60,400}$/.test(token)) return json_({ ok: false, error: 'bad_token' });

  // Не больше MAX_PER_MINUTE пушей в минуту на один телефон: защита от случайной лавины.
  var cache = CacheService.getScriptCache();
  var counter = 'n:' + Utilities.base64EncodeWebSafe(
    Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, token)).substring(0, 32);
  var count = Number(cache.get(counter) || 0);
  if (count >= MAX_PER_MINUTE) return json_({ ok: false, error: 'too_many' });
  cache.put(counter, String(count + 1), 60);

  var data = {};
  var source = request.data || {};
  Object.keys(source).slice(0, 8).forEach(function (key) {
    data[String(key).substring(0, 16)] = String(source[key]).substring(0, 3000);
  });

  var account = serviceAccount_();
  var reply = UrlFetchApp.fetch('https://fcm.googleapis.com/v1/projects/' + account.project_id + '/messages:send', {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + accessToken_(account) },
    payload: JSON.stringify({
      message: { token: token, data: data, android: { priority: 'high', ttl: '86400s' } }
    })
  });
  var code = reply.getResponseCode();
  if (code === 200) return json_({ ok: true });
  var body = reply.getContentText();
  if (code === 404 || body.indexOf('UNREGISTERED') >= 0) return json_({ ok: false, error: 'unregistered' });
  console.warn('FCM ответил ' + code + ': ' + body.substring(0, 300));
  return json_({ ok: false, error: 'fcm_' + code });
}

/**
 * Проверка настройки: выбери вверху функцию check и нажми «Выполнить».
 * В журнале должно появиться «Всё готово».
 */
function check() {
  var props = PropertiesService.getScriptProperties();
  var appId = props.getProperty('APP_ID') || '';
  if (!/^1:\d+:android:[0-9a-f]+$/.test(appId)) {
    throw new Error('Свойство APP_ID не задано или неверное. Оно выглядит как 1:1234567890:android:abc123…');
  }
  var account = serviceAccount_();
  accessToken_(account, true);
  console.log('Всё готово: проект ' + account.project_id + ', приложение ' + appId +
    '. Теперь «Начать развёртывание» → «Новое развёртывание» → «Веб-приложение».');
}

function serviceAccount_() {
  var raw = PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT');
  if (!raw) throw new Error('Свойство SERVICE_ACCOUNT не задано: вставь туда всё содержимое JSON-файла ключа.');
  var account;
  try {
    account = JSON.parse(raw);
  } catch (err) {
    throw new Error('SERVICE_ACCOUNT не читается как JSON: скопируй файл ключа целиком, от { до }.');
  }
  if (!account.private_key || !account.client_email || !account.project_id) {
    throw new Error('В SERVICE_ACCOUNT нет нужных полей: это должен быть ключ сервисного аккаунта из Firebase.');
  }
  return account;
}

/** Токен доступа к Firebase по ключу сервисного аккаунта (действует час, хранится в кэше). */
function accessToken_(account, fresh) {
  var cache = CacheService.getScriptCache();
  if (!fresh) {
    var saved = cache.get('fcm-access');
    if (saved) return saved;
  }
  var now = Math.floor(Date.now() / 1000);
  var header = b64_(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  var claims = b64_(JSON.stringify({
    iss: account.client_email,
    scope: FCM_SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600
  }));
  var input = header + '.' + claims;
  var signature = Utilities.base64EncodeWebSafe(Utilities.computeRsaSha256Signature(input, account.private_key))
    .replace(/=+$/, '');
  var reply = UrlFetchApp.fetch(TOKEN_URL, {
    method: 'post',
    muteHttpExceptions: true,
    payload: { grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: input + '.' + signature }
  });
  if (reply.getResponseCode() !== 200) {
    throw new Error('Google не принял ключ сервисного аккаунта (' + reply.getResponseCode() + '): ' +
      reply.getContentText().substring(0, 200));
  }
  var token = JSON.parse(reply.getContentText()).access_token;
  cache.put('fcm-access', token, 3000);
  return token;
}

function b64_(text) {
  return Utilities.base64EncodeWebSafe(Utilities.newBlob(text).getBytes()).replace(/=+$/, '');
}

function json_(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}
