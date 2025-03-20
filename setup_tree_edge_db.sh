#!/bin/bash

# Variables
DB_NAME="tree_edge_db"
DB_USER="prewave"
DB_PASSWORD="prew4vePwd"
INIT_SQL_PATH="init.sql" # Path to the init.sql file

# Create the PostgreSQL user and database
psql -v ON_ERROR_STOP=1 <<-EOSQL
    -- Create user
    DO
    \$\$
    BEGIN
        IF NOT EXISTS (
            SELECT FROM pg_catalog.pg_roles WHERE rolname = '${DB_USER}'
        ) THEN
            CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASSWORD}';
        END IF;
    END
    \$\$;

    -- Create database
    DO
    \$\$
    BEGIN
        IF NOT EXISTS (
            SELECT FROM pg_database WHERE datname = '${DB_NAME}'
        ) THEN
            CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};
        END IF;
    END
    \$\$;

    -- Grant privileges
    GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
EOSQL

# Connect to the database and create the table
psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" <<-EOSQL
    -- Create the edge table
    CREATE TABLE IF NOT EXISTS edge (
        from_id INTEGER NOT NULL,
        to_id INTEGER NOT NULL,
        CONSTRAINT edge_unique UNIQUE (from_id, to_id)
    );

    -- Grant ownership of the table to the user
    ALTER TABLE edge OWNER TO ${DB_USER};
EOSQL

# Initialize the database using the init.sql file
if [ -f "${INIT_SQL_PATH}" ]; then
    echo "Initializing database with ${INIT_SQL_PATH}..."
    psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -U "${DB_USER}" -f "${INIT_SQL_PATH}"
else
    echo "Error: ${INIT_SQL_PATH} file not found. Skipping initialization."
fi

echo "Database setup complete."
