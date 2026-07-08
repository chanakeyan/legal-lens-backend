package com.example.legal.lens;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LegalLensController {

	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("ok", "Legal Lens backend is running");
	}

	@GetMapping("/examples")
	public List<ExampleDocument> examples() {
		return List.of(
				new ExampleDocument("Rental Agreement",
						"The tenant shall pay a late fee of 10% if rent is delayed. The landlord may terminate this agreement with 7 days notice."),
				new ExampleDocument("Employment Clause",
						"The employee must not disclose confidential information and may be terminated without notice for breach of this agreement."),
				new ExampleDocument("Service Contract",
						"The provider shall deliver services within 30 days. Either party may cancel with written notice and payment of outstanding fees."));
	}

	@PostMapping("/analyze")
	public AnalysisResponse analyze(@RequestBody AnalysisRequest request) {
		String document = request == null || request.text() == null ? "" : request.text().trim();
		if (document.isBlank()) {
			return new AnalysisResponse("Empty document", 0, List.of(), List.of("Paste a legal clause or document to begin."), List.of());
		}

		String lower = document.toLowerCase(Locale.ROOT);
		List<RiskItem> risks = findRisks(lower);
		List<String> highlights = findHighlights(lower);
		List<String> nextSteps = nextSteps(risks, highlights);
		int score = Math.max(5, 100 - risks.stream().mapToInt(RiskItem::scoreImpact).sum());
		String summary = summaryFor(score, risks.size(), highlights.size());

		return new AnalysisResponse(summary, score, risks, highlights, nextSteps);
	}

	private List<RiskItem> findRisks(String lower) {
		List<RiskItem> risks = new ArrayList<>();

		if (containsAny(lower, "terminate", "termination", "cancel")) {
			risks.add(new RiskItem("Termination clause", "Check whether notice period and cancellation rights are fair for both sides.", "Medium", 18));
		}
		if (containsAny(lower, "penalty", "late fee", "fine", "interest")) {
			risks.add(new RiskItem("Payment penalty", "The document includes a fee or penalty. Confirm the amount is reasonable and legally permitted.", "High", 24));
		}
		if (containsAny(lower, "without notice", "immediately", "sole discretion")) {
			risks.add(new RiskItem("One-sided discretion", "One party may have broad power to act without warning or review.", "High", 26));
		}
		if (containsAny(lower, "confidential", "non-disclosure", "proprietary")) {
			risks.add(new RiskItem("Confidentiality duty", "Make sure the confidentiality scope, duration, and exceptions are clear.", "Medium", 14));
		}
		if (containsAny(lower, "indemnify", "liability", "damages")) {
			risks.add(new RiskItem("Liability exposure", "Review who pays for losses, damages, claims, or third-party costs.", "High", 22));
		}
		if (containsAny(lower, "arbitration", "jurisdiction", "governing law", "court")) {
			risks.add(new RiskItem("Dispute forum", "The document controls where and how disputes are handled.", "Medium", 12));
		}

		if (risks.isEmpty()) {
			risks.add(new RiskItem("No major red flags detected", "The text does not match common high-risk terms, but it still needs human review.", "Low", 8));
		}

		return risks;
	}

	private List<String> findHighlights(String lower) {
		Set<String> highlights = new LinkedHashSet<>();

		if (containsAny(lower, "shall", "must", "required")) {
			highlights.add("Mandatory obligations are present.");
		}
		if (containsAny(lower, "days", "date", "deadline", "notice")) {
			highlights.add("Timeline or notice requirements should be tracked.");
		}
		if (containsAny(lower, "payment", "fee", "rent", "salary", "invoice")) {
			highlights.add("Payment terms appear in the document.");
		}
		if (containsAny(lower, "agreement", "contract", "party", "parties")) {
			highlights.add("Contract relationship language detected.");
		}
		if (highlights.isEmpty()) {
			highlights.add("The text is readable, but it has limited legal keywords.");
		}

		return List.copyOf(highlights);
	}

	private List<String> nextSteps(List<RiskItem> risks, List<String> highlights) {
		List<String> steps = new ArrayList<>();
		steps.add("Confirm names, dates, amounts, notice periods, and governing law.");
		steps.add("Ask a qualified lawyer before signing or relying on this analysis.");
		if (risks.stream().anyMatch(risk -> "High".equals(risk.severity()))) {
			steps.add("Negotiate or clarify high-risk terms before accepting the document.");
		}
		if (!highlights.isEmpty()) {
			steps.add("Create reminders for every deadline or notice period mentioned.");
		}
		return steps;
	}

	private String summaryFor(int score, int riskCount, int highlightCount) {
		if (score >= 80) {
			return "This document looks relatively clear, with a few items worth reviewing.";
		}
		if (score >= 55) {
			return "This document has some important clauses that should be checked carefully.";
		}
		return "This document includes several risky terms and needs careful legal review.";
	}

	private boolean containsAny(String text, String... terms) {
		for (String term : terms) {
			if (text.contains(term)) {
				return true;
			}
		}
		return false;
	}

	public record AnalysisRequest(String text) {
	}

	public record AnalysisResponse(String summary, int score, List<RiskItem> risks, List<String> highlights, List<String> nextSteps) {
	}

	public record RiskItem(String title, String detail, String severity, int scoreImpact) {
	}

	public record ExampleDocument(String title, String text) {
	}

	public record HealthResponse(String status, String message) {
	}
}
