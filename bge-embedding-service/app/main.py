
import os
import math
from functools import lru_cache
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

MODEL_NAME = os.getenv(
    "MODEL_NAME",
    "BAAI/bge-small-en-v1.5"
)

MAX_BATCH_SIZE = 32
DEVICE = os.getenv("DEVICE", "cpu")

app = FastAPI(
    title="ShopSphere BGE Embedding Service",
    version="1.0.0"
)


class EmbeddingRequest(BaseModel):
    input: list[str] = Field(min_length=1, max_length=32)
    input_type: Literal["document", "query"] = "document"


class EmbeddingItem(BaseModel):
    index: int
    embedding: list[float]


class EmbeddingResponse(BaseModel):
    model: str
    dimensions: int
    data: list[EmbeddingItem]


@lru_cache(maxsize=1)
def get_model():
    return SentenceTransformer(
        MODEL_NAME,
        device=DEVICE
    )


@app.get("/health")
def health():
    try:
        model = get_model()

        return {
            "status": "UP",
            "model": MODEL_NAME,
            "dimensions": model.get_sentence_embedding_dimension(),
            "device": DEVICE
        }

    except Exception as exc:
        raise HTTPException(
            status_code=503,
            detail=str(exc)
        )


@app.post("/v1/embeddings")
def create_embeddings(request: EmbeddingRequest):

    if len(request.input) > MAX_BATCH_SIZE:
        raise HTTPException(
            status_code=413,
            detail="Maximum batch size is 32"
        )

    texts = []

    for index, text in enumerate(request.input):

        if not text or not text.strip():
            raise HTTPException(
                status_code=400,
                detail=f"Input {index} is empty"
            )

        if len(text) > 4000:
            raise HTTPException(
                status_code=400,
                detail=f"Input {index} is too long"
            )

        texts.append(text.strip())

    # BGE uses a retrieval instruction for search queries.
    # Product documents do not receive this instruction.
    if request.input_type == "query":
        texts = [
            "Represent this sentence for searching relevant products: "
            + text
            for text in texts
        ]

    try:
        model = get_model()

        vectors = model.encode(
            texts,
            batch_size=min(len(texts), MAX_BATCH_SIZE),
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False
        )

    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Embedding generation failed: {exc}"
        )

    dimensions = model.get_sentence_embedding_dimension()

    data = []

    for index, vector in enumerate(vectors):

        values = [float(value) for value in vector.tolist()]

        if len(values) != dimensions:
            raise HTTPException(
                status_code=500,
                detail="Invalid embedding dimensions"
            )

        if not all(math.isfinite(value) for value in values):
            raise HTTPException(
                status_code=500,
                detail="Invalid embedding values"
            )

        data.append({
            "index": index,
            "embedding": values
        })

    return {
        "model": MODEL_NAME,
        "dimensions": dimensions,
        "data": data
    }
