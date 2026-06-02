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


def test_parser_parses_structured_extraction_with_source_quotes() -> None:
    from langchain_core.output_parsers import PydanticOutputParser

    parser = PydanticOutputParser(pydantic_object=DocumentAnalysisResult)
    obj = parser.parse(
        '{"summary":"백엔드","tech_stack":["Go"],'
        '"projects":[{"name":"결제","role":"BE","contribution":"분산락","stack":["Go"],'
        '"source_quote":"결제 시스템에서 분산락을 도입"}],'
        '"experiences":[{"title":"스타트업","detail":"3년","source_quote":"3년 차"}],'
        '"skills":[{"name":"Kafka","evidence":"Kafka 로 처리량 3배"}],'
        '"markdown":"## 개요\\n..."}'
    )
    assert obj.projects[0].name == "결제"
    assert obj.projects[0].source_quote == "결제 시스템에서 분산락을 도입"
    assert obj.experiences[0].title == "스타트업"
    assert obj.skills[0].evidence == "Kafka 로 처리량 3배"


def test_result_backward_compatible_defaults() -> None:
    # 구조화 필드 없이도(기존 호출부) 생성 가능 — 콜백 계약 불변 보장
    r = DocumentAnalysisResult(summary="s", tech_stack=["x"], markdown="## 개요")
    assert r.projects == []
    assert r.experiences == []
    assert r.skills == []
