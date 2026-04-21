-- Invoice and reminder emails historically stored their rendered HTML in
-- `body` instead of `body_html`, so the show page escaped it and displayed
-- the raw HTML source. Move any clearly-HTML bodies over to `body_html`.

UPDATE emails
SET body_html = body,
    body = ''
WHERE body_html IS NULL
  AND (body ILIKE '<!doctype%' OR body ILIKE '<html%');
