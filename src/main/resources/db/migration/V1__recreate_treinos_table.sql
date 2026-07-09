CREATE TABLE IF NOT EXISTS treinos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_treino DATE NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    distancia_km DECIMAL(5,2) NOT NULL,
    tempo_minutos INT NOT NULL,
    pace_medio VARCHAR(10),
    observacoes TEXT
);
