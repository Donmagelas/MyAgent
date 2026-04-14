UPDATE knowledge_chunk
SET content_tsv = to_tsvector(
        'simple',
        concat_ws(
                E'\n',
                nullif(trim(metadata_json ->> 'chunkTitle'), ''),
                nullif(trim(metadata_json ->> 'chunkSummary'), ''),
                content
        )
)
WHERE content_tsv IS NULL
   OR content_tsv <> to_tsvector(
        'simple',
        concat_ws(
                E'\n',
                nullif(trim(metadata_json ->> 'chunkTitle'), ''),
                nullif(trim(metadata_json ->> 'chunkSummary'), ''),
                content
        )
);
