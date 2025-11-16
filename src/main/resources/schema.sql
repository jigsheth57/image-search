CREATE TABLE vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT, -- The actual text content of the document (your .txt file descriptions)
    metadata JSON, -- The associated metadata (like image_file_name)
    -- The VECTOR type requires a dimension size. Replace 384 with your model's dimension size.
    embedding VECTOR(384)
);
CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops) WITH (M = 16, EF_CONSTRUCTION = 128);