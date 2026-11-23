-- Données de test pour movieService (PostgreSQL)
-- À exécuter UNE SEULE FOIS, après un premier démarrage de movieService
-- (Hibernate crée les tables au démarrage).
INSERT INTO category (category_name) VALUES ('Action');
INSERT INTO director (director_name) VALUES ('Christopher Nolan');
INSERT INTO movie (movie_name, description, duration, release_date, is_display, movie_trailer_url, category_id, director_id)
VALUES ('Film de test', 'Film pour le test de reference', 120, '2026-01-01', true, 'https://www.youtube.com/embed/dQw4w9WgXcQ', 1, 1);
INSERT INTO movie_image (image_url, movie_movie_id) VALUES ('/images/topgun.png', 1);
