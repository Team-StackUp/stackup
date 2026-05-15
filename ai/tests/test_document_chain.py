import pytest
from langchain_core.runnables import RunnableLambda

from ai_server.chain.document_analysis_chain import (
    DocumentAnalysisResult,
    LlmDocumentAnalyzer,
)


@pytest.mark.asyncio
async def test_llm_document_analyzer_invokes_chain_with_inputs() -> None:
    fake_result = DocumentAnalysisResult(
        summary="요약", tech_stack=["Python"], markdown="## 개요\n..."
    )
    captured: dict = {}

    async def fake_invoke(inputs: dict) -> DocumentAnalysisResult:
        captured.update(inputs)
        return fake_result

    analyzer = LlmDocumentAnalyzer(RunnableLambda(fake_invoke))
    out = await analyzer.analyze(text="raw text", source_type="PDF")

    assert captured == {"text": "raw text", "source_type": "PDF"}
    assert out is fake_result


@pytest.mark.asyncio
async def test_llm_document_analyzer_rejects_wrong_type() -> None:
    async def fake_invoke(_: dict) -> dict:
        return {"summary": "not a model"}

    analyzer = LlmDocumentAnalyzer(RunnableLambda(fake_invoke))
    with pytest.raises(TypeError):
        await analyzer.analyze(text="x", source_type="PDF")
