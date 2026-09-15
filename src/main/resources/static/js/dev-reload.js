// Spring Boot ships the LiveReload client. Load it only on the local dev server.
if (['localhost', '127.0.0.1'].includes(location.hostname)) {
  const script = document.createElement('script');
  script.src = `http://${location.hostname}:35729/livereload.js?snipver=1`;
  document.head.append(script);
}
