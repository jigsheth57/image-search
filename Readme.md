# image-search
## Intelligent (semantic similarity) searching images based on Vision LLM automatically generated a detailed, descriptive caption

### Setup in Macos

#### Install Postgres

`brew install postgresql@18`

`brew install pgvector`

`export PATH="/opt/homebrew/opt/postgresql@18/bin:$PATH"`

`export LDFLAGS="-L/opt/homebrew/opt/postgresql@18/lib"`

`export CPPFLAGS="-I/opt/homebrew/opt/postgresql@18/include"`

`brew services start postgresql@18`

#### configure postgres for Vector extension and a user

`psql postgres`

`CREATE ROLE myappuser WITH LOGIN PASSWORD 'mypassword';`

`CREATE DATABASE imagesearchdb OWNER myappuser;`

`GRANT ALL PRIVILEGES ON DATABASE imagesearchdb TO myappuser;`

`GRANT USAGE ON SCHEMA public TO myappuser;`

`GRANT CREATE ON SCHEMA public TO myappuser;`

`CREATE EXTENSION vector;`

#### Install Ollama & download LLM models

`brew install ollama`

`brew services start ollama`

`ollama pull mxbai-embed-large`

