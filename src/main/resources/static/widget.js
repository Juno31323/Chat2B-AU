(function () {
  const script = document.currentScript;
  if (!script) {
    return;
  }

  const scriptUrl = new URL(script.src, window.location.href);
  const baseUrl = new URL(".", scriptUrl).href;
  const embedUrl = script.dataset.chatUrl || new URL("embed.html", baseUrl).href;
  const chatIconUrl = new URL("ansan-quick-chat.svg", baseUrl).href;
  const label = script.dataset.label || "입학상담";
  const position = (script.dataset.position || "right").toLowerCase() === "left" ? "left" : "right";
  const width = normalizeSize(script.dataset.width, "430px");
  const height = normalizeSize(script.dataset.height, "700px");
  const startOpen = (script.dataset.open || "false").toLowerCase() === "true";

  injectStyles(position, width, height);

  const root = document.createElement("div");
  root.className = "admissions-widget-root";
  root.innerHTML = `
    <button type="button" class="admissions-widget-toggle" aria-expanded="false" aria-controls="admissions-widget-panel">
      <span class="admissions-widget-toggle-mark">
        <img src="${escapeAttribute(chatIconUrl)}" alt="">
      </span>
      <span class="admissions-widget-toggle-copy">
        <strong>${escapeHtml(label)}</strong>
        <small>안산대학교 입학안내</small>
      </span>
    </button>
    <section id="admissions-widget-panel" class="admissions-widget-panel" aria-hidden="true">
      <iframe
        class="admissions-widget-frame"
        src="${escapeAttribute(embedUrl)}"
        title="안산대학교 입학상담 챗봇"
        loading="lazy"
        referrerpolicy="strict-origin-when-cross-origin"></iframe>
    </section>
  `;

  document.body.appendChild(root);

  const toggle = root.querySelector(".admissions-widget-toggle");
  const panel = root.querySelector(".admissions-widget-panel");

  function setOpen(isOpen) {
    root.classList.toggle("is-open", isOpen);
    toggle.setAttribute("aria-expanded", String(isOpen));
    panel.setAttribute("aria-hidden", String(!isOpen));
  }

  toggle.addEventListener("click", () => {
    setOpen(!root.classList.contains("is-open"));
  });
  if (startOpen) {
    setOpen(true);
  }

  function injectStyles(positionValue, widthValue, heightValue) {
    if (document.getElementById("admissions-widget-style")) {
      return;
    }

    const style = document.createElement("style");
    style.id = "admissions-widget-style";
    style.textContent = `
      .admissions-widget-root {
        position: fixed;
        ${positionValue}: 24px;
        bottom: 20px;
        z-index: 2147483000;
        font-family: "Segoe UI", "Noto Sans KR", sans-serif;
      }

      .admissions-widget-toggle {
        border: 0;
        cursor: pointer;
        font: inherit;
      }

      .admissions-widget-toggle {
        display: inline-flex;
        align-items: center;
        gap: 12px;
        min-width: 188px;
        padding: 11px 15px 11px 11px;
        border-radius: 999px;
        background: linear-gradient(135deg, #143886, #204faa);
        color: #fff;
        box-shadow: 0 20px 44px rgba(16, 46, 118, 0.28);
      }

      .admissions-widget-toggle-mark {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.16);
        box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
      }

      .admissions-widget-toggle-mark img {
        width: 18px;
        height: 18px;
        filter: brightness(0) invert(1);
      }

      .admissions-widget-toggle-copy {
        display: grid;
        gap: 2px;
        text-align: left;
      }

      .admissions-widget-toggle-copy strong {
        font-size: 0.95rem;
        line-height: 1.1;
      }

      .admissions-widget-toggle-copy small {
        font-size: 0.72rem;
        color: rgba(255, 255, 255, 0.78);
      }

      .admissions-widget-panel {
        position: absolute;
        ${positionValue}: 0;
        bottom: 76px;
        width: min(${widthValue}, calc(100vw - 32px));
        height: min(${heightValue}, calc(100vh - 140px));
        height: min(${heightValue}, calc(100dvh - 140px));
        max-height: calc(100vh - 140px);
        max-height: calc(100dvh - 140px);
        display: none;
        overflow: hidden;
        border-radius: 24px;
        background: #ffffff;
        border: 1px solid rgba(21, 34, 72, 0.12);
        box-shadow: 0 34px 86px rgba(15, 33, 80, 0.24);
      }

      .admissions-widget-root.is-open .admissions-widget-panel {
        display: block;
      }

      .admissions-widget-frame {
        width: 100%;
        height: 100%;
        border: 0;
        background: #f7faff;
      }

      @media (max-width: 900px) {
        .admissions-widget-root {
          left: 12px;
          right: 12px;
          bottom: 12px;
        }

        .admissions-widget-toggle {
          width: 100%;
          justify-content: flex-start;
        }

        .admissions-widget-panel {
          left: 0;
          right: 0;
          width: 100%;
          height: calc(100vh - 156px - env(safe-area-inset-top));
          height: calc(100dvh - 156px - env(safe-area-inset-top));
          max-height: calc(100vh - 156px - env(safe-area-inset-top));
          max-height: calc(100dvh - 156px - env(safe-area-inset-top));
          bottom: calc(60px + env(safe-area-inset-bottom));
          border-radius: 22px;
        }
      }
    `;

    document.head.appendChild(style);
  }

  function normalizeSize(value, fallback) {
    if (!value) {
      return fallback;
    }
    return /^[0-9]+$/.test(value) ? `${value}px` : value;
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
  }

  function escapeAttribute(value) {
    return escapeHtml(value).replaceAll('"', "&quot;");
  }
})();
