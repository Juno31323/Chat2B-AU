const form = document.querySelector("#chat-form");
const questionInput = document.querySelector("#question");
const messages = document.querySelector("#messages");
const sourceTemplate = document.querySelector("#source-template");

const COPY = {
  emptyInputTitle: "\uC9C8\uBB38\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694",
  emptyInputDetail: "\uB0B4\uC6A9\uC744 \uC785\uB825\uD55C \uB4A4 \uB2E4\uC2DC \uBCF4\uB0B4 \uC8FC\uC138\uC694.",
  emptyInputAction: "\uC9E7\uAC8C \uD55C \uBB38\uC7A5\uC73C\uB85C \uC785\uB825\uD574\uB3C4 \uAD1C\uCC2E\uC544\uC694.",
  pending: "\uB2F5\uBCC0\uC744 \uC900\uBE44\uD558\uACE0 \uC788\uC5B4\uC694.",
  networkTitle: "\uC9C0\uAE08\uC740 \uB2F5\uBCC0\uC744 \uC900\uBE44\uD558\uC9C0 \uBABB\uD558\uACE0 \uC788\uC5B4\uC694",
  networkDetail: "\uC77C\uC2DC\uC801\uC73C\uB85C \uC774\uC6A9\uC774 \uC6D0\uD65C\uD558\uC9C0 \uC54A\uC544\uC694.",
  networkAction: "\uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.",
  sourceAriaSuffix: " \uADFC\uAC70 \uBCF4\uAE30",
  noticeBadge: "\uC548\uB0B4",
  fallbackBadRequestTitle: "\uC9C8\uBB38\uC744 \uB2E4\uC2DC \uD655\uC778\uD574 \uC8FC\uC138\uC694",
  fallbackBadRequestDetail: "\uC785\uB825\uD55C \uB0B4\uC6A9\uC744 \uCC98\uB9AC\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694.",
  fallbackBadRequestAction: "\uC9C8\uBB38\uC744 \uC870\uAE08 \uB354 \uC9E7\uACE0 \uBD84\uBA85\uD558\uAC8C \uC801\uC5B4 \uC8FC\uC138\uC694.",
  fallbackRateLimitTitle: "\uC694\uCCAD\uC774 \uC7A0\uC2DC \uB9CE\uC544\uC694",
  fallbackRateLimitDetail: "\uC870\uAE08 \uD6C4\uC5D0 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.",
  fallbackRateLimitAction: "\uC870\uAE08 \uC788\uB2E4\uAC00 \uB2E4\uC2DC \uC9C8\uBB38\uD574 \uC8FC\uC138\uC694.",
  fallbackServerTitle: "\uC9C0\uAE08\uC740 \uB2F5\uBCC0\uC744 \uC900\uBE44\uD558\uC9C0 \uBABB\uD558\uACE0 \uC788\uC5B4\uC694",
  fallbackServerDetail: "\uC77C\uC2DC\uC801\uC778 \uBB38\uC81C\uB85C \uB2F5\uBCC0\uC774 \uC9C0\uC5F0\uB418\uACE0 \uC788\uC5B4\uC694.",
  fallbackServerAction: "\uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694. \uBB38\uC81C\uAC00 \uACC4\uC18D\uB418\uBA74 \uC785\uD559\uCC98\uB85C \uBB38\uC758\uD574 \uC8FC\uC138\uC694.",
  fallbackUnknownTitle: "\uD604\uC7AC \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC5B4\uC694",
  fallbackUnknownDetail: "\uC694\uCCAD\uC744 \uCC98\uB9AC\uD558\uB294 \uC911 \uBB38\uC81C\uAC00 \uC0DD\uACBC\uC5B4\uC694.",
  fallbackUnknownAction: "\uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."
};

const sessionId = getSessionId();

document.addEventListener("click", (event) => {
  const chip = event.target.closest(".source-chip");
  const item = event.target.closest(".source-item");
  if (!chip || !item) {
    closeSourcePreviews();
    return;
  }

  const shouldOpen = !item.classList.contains("is-open");
  closeSourcePreviews();
  item.classList.toggle("is-open", shouldOpen);
  chip.setAttribute("aria-expanded", String(shouldOpen));
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeSourcePreviews();
  }
});

questionInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    form.requestSubmit();
  }
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const question = questionInput.value.trim();
  if (!question) {
    appendSystemMessage({
      title: COPY.emptyInputTitle,
      detail: COPY.emptyInputDetail,
      action: COPY.emptyInputAction
    });
    return;
  }

  closeSourcePreviews();
  const userMessage = appendMessage("user", question, { scroll: false });
  questionInput.value = "";

  const pending = appendMessage("assistant", COPY.pending, { scroll: false });
  focusConversation(userMessage);

  try {
    const response = await fetch("/api/chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        question,
        sessionId
      })
    });

    const data = await readJsonSafely(response);
    if (!response.ok) {
      pending.replaceWith(buildErrorMessage(normalizeProblem(data, response.status)));
      focusConversation(userMessage);
      return;
    }

    const assistantMessage = buildAssistantMessage(data.answer, data.sources);
    pending.replaceWith(assistantMessage);
    focusConversation(userMessage);
  } catch (error) {
    pending.replaceWith(
      buildErrorMessage({
        title: COPY.networkTitle,
        detail: COPY.networkDetail,
        action: COPY.networkAction
      })
    );
    focusConversation(userMessage);
  }
});

function appendMessage(role, text, options = {}) {
  const article = createMessage(role, text);
  messages.appendChild(article);
  if (options.scroll !== false) {
    messages.scrollTop = messages.scrollHeight;
  }
  return article;
}

function appendSystemMessage(problem) {
  const article = buildErrorMessage(problem);
  messages.appendChild(article);
  messages.scrollTop = messages.scrollHeight;
  return article;
}

function createMessage(role, text) {
  const article = document.createElement("article");
  article.className = `message ${role}`;
  article.appendChild(buildMessageBody(role, text));
  return article;
}

function buildAssistantMessage(answer, sources = []) {
  const article = createMessage("assistant", answer);

  if (sources.length > 0) {
    const list = document.createElement("ul");
    list.className = "sources";

    sources.slice(0, 1).forEach((source) => {
      const item = sourceTemplate.content.firstElementChild.cloneNode(true);
      const label = normalizeSourceLabel(source.label);
      const snippet = normalizeSourceSnippet(source.snippet);
      item.querySelector(".source-chip").setAttribute("aria-label", `${label}${COPY.sourceAriaSuffix}`);
      item.querySelector(".source-label").textContent = label;
      item.querySelector(".source-snippet").textContent = snippet;
      list.appendChild(item);
    });

    article.appendChild(list);
  }

  return article;
}

function buildErrorMessage(problem) {
  const normalized = normalizeProblem(problem);
  const article = document.createElement("article");
  article.className = "message assistant message-error";

  const wrapper = document.createElement("div");
  wrapper.className = "message-feedback";

  const badge = document.createElement("span");
  badge.className = "message-feedback-badge";
  badge.textContent = COPY.noticeBadge;

  const title = document.createElement("strong");
  title.className = "message-feedback-title";
  title.textContent = normalized.title;

  const detail = document.createElement("p");
  detail.className = "message-feedback-detail";
  detail.textContent = normalized.detail;

  wrapper.appendChild(badge);
  wrapper.appendChild(title);
  wrapper.appendChild(detail);

  if (normalized.action) {
    const action = document.createElement("p");
    action.className = "message-feedback-action";
    action.textContent = normalized.action;
    wrapper.appendChild(action);
  }

  article.appendChild(wrapper);
  return article;
}

function normalizeProblem(problem, status = 0) {
  const fallback = fallbackProblem(status);
  if (problem && typeof problem === "object") {
    const title = cleanText(problem.title);
    const detail = cleanText(problem.detail);
    const action = cleanText(problem.action);
    if (title || detail || action) {
      return {
        title: title || fallback.title,
        detail: detail || fallback.detail,
        action: action || fallback.action
      };
    }
  }

  return fallback;
}

function fallbackProblem(status) {
  if (status === 400) {
    return {
      title: COPY.fallbackBadRequestTitle,
      detail: COPY.fallbackBadRequestDetail,
      action: COPY.fallbackBadRequestAction
    };
  }
  if (status === 429) {
    return {
      title: COPY.fallbackRateLimitTitle,
      detail: COPY.fallbackRateLimitDetail,
      action: COPY.fallbackRateLimitAction
    };
  }
  if (status >= 500) {
    return {
      title: COPY.fallbackServerTitle,
      detail: COPY.fallbackServerDetail,
      action: COPY.fallbackServerAction
    };
  }
  return {
    title: COPY.fallbackUnknownTitle,
    detail: COPY.fallbackUnknownDetail,
    action: COPY.fallbackUnknownAction
  };
}

async function readJsonSafely(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    return null;
  }
}

function buildMessageBody(role, text) {
  if (role !== "assistant") {
    const paragraph = document.createElement("p");
    paragraph.textContent = text;
    return paragraph;
  }

  const body = document.createElement("div");
  body.className = "message-body";
  const normalized = String(text ?? "").replace(/\r/g, "").trim();
  if (!normalized) {
    const emptyParagraph = document.createElement("p");
    emptyParagraph.textContent = "";
    body.appendChild(emptyParagraph);
    return body;
  }

  let paragraphLines = [];
  let list = null;

  const flushParagraph = () => {
    if (paragraphLines.length === 0) {
      return;
    }
    const paragraph = document.createElement("p");
    paragraph.textContent = inlineMarkdownToText(paragraphLines.join(" "));
    body.appendChild(paragraph);
    paragraphLines = [];
  };

  const ensureList = () => {
    if (!list) {
      list = document.createElement("ul");
      body.appendChild(list);
    }
    return list;
  };

  const flushList = () => {
    list = null;
  };

  normalized.split("\n").forEach((rawLine) => {
    const line = rawLine.trim();
    if (!line) {
      flushParagraph();
      flushList();
      return;
    }

    const bulletMatch = line.match(/^[-*]\s+(.+)$/);
    if (bulletMatch) {
      flushParagraph();
      const item = document.createElement("li");
      item.textContent = inlineMarkdownToText(bulletMatch[1]);
      ensureList().appendChild(item);
      return;
    }

    if (/^#{1,6}\s+/.test(line)) {
      flushParagraph();
      flushList();
      const paragraph = document.createElement("p");
      paragraph.className = "message-heading";
      paragraph.textContent = inlineMarkdownToText(line.replace(/^#{1,6}\s+/, ""));
      body.appendChild(paragraph);
      return;
    }

    flushList();
    paragraphLines.push(line);
  });

  flushParagraph();
  return body;
}

function inlineMarkdownToText(value) {
  return cleanText(
    String(value ?? "")
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1")
      .replace(/[*_`>#]+/g, " ")
  );
}

function focusConversation(userMessage) {
  const topPadding = Math.min(48, Math.max(24, Math.round(messages.clientHeight * 0.08)));
  const top = Math.max(0, userMessage.offsetTop - topPadding);
  messages.scrollTop = top;
}

function shortenText(value, maxLength) {
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength - 3).trimEnd()}...`;
}

function cleanText(value) {
  return String(value ?? "")
    .replace(/\s+/g, " ")
    .trim();
}

function normalizeSourceLabel(value) {
  const parts = cleanText(value)
    .split(/\s*\/\s*/)
    .map((part) => cleanText(part))
    .filter(Boolean);

  const deduped = [];
  parts.forEach((part) => {
    if (!deduped.some((existing) => areNearDuplicate(existing, part))) {
      deduped.push(part);
    }
  });

  return shortenText(deduped.join(" - ") || cleanText(value), 56);
}

function normalizeSourceSnippet(value) {
  const normalized = cleanText(value).replace(/https?:\/\/\S+/gi, (url) => shortenUrl(url));
  return shortenText(normalized, 180);
}

function shortenUrl(url) {
  try {
    const parsed = new URL(url);
    const path = parsed.pathname.length > 28
      ? `${parsed.pathname.slice(0, 28)}...`
      : parsed.pathname;
    return `${parsed.hostname}${path}`;
  } catch (error) {
    return shortenText(url, 48);
  }
}

function areNearDuplicate(left, right) {
  const normalize = (value) => cleanText(value).toLowerCase().replace(/[.\-_/()\[\]\s]+/g, "");
  const leftNormalized = normalize(left);
  const rightNormalized = normalize(right);
  return leftNormalized === rightNormalized
    || leftNormalized.includes(rightNormalized)
    || rightNormalized.includes(leftNormalized);
}

function closeSourcePreviews() {
  document.querySelectorAll(".source-item.is-open").forEach((item) => {
    item.classList.remove("is-open");
    const chip = item.querySelector(".source-chip");
    if (chip) {
      chip.setAttribute("aria-expanded", "false");
    }
  });
}

function getSessionId() {
  const key = "admissions-chatbot-session";
  let value = window.localStorage.getItem(key);
  if (!value) {
    value = crypto.randomUUID();
    window.localStorage.setItem(key, value);
  }
  return value;
}
