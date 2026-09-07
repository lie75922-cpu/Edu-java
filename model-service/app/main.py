from fastapi import FastAPI

app = FastAPI(title="Edu Model Service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "UP",
        "service": "model-service",
        "model_loaded": False,
        "version": "0.1.0",
    }
