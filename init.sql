CREATE TABLE edge (
    from_id INT NOT NULL,
    to_id INT NOT NULL,
    PRIMARY KEY (from_id, to_id)
);

INSERT INTO edge (from_id, to_id) VALUES (1, 2), (1, 3), (2, 4), (2, 5), (3, 6);
