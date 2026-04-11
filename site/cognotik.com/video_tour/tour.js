/**
 * Cognotik Interactive Video Tour
 *
 * Manages navigation between tour chapters, video playback,
 * transcript display, autoplay sequencing, and progress tracking.
 */
(function () {
    "use strict";

    // ── State ──────────────────────────────────────────────────────────
    let currentIndex = -1;
    let autoplayMode = false;

    // ── DOM References ─────────────────────────────────────────────────
    const overviewPanel = document.getElementById("overview-panel");
    const playerPanel = document.getElementById("player-panel");
    const overviewGrid = document.getElementById("overview-grid");
    const chapterList = document.getElementById("chapter-list");
    const video = document.getElementById("tour-video");
    const videoSource = document.getElementById("video-source");
    const currentTitle = document.getElementById("current-title");
    const currentDescription = document.getElementById("current-description");
    const chapterIndicator = document.getElementById("chapter-indicator");
    const btnPrev = document.getElementById("btn-prev");
    const btnNext = document.getElementById("btn-next");
    const btnOverview = document.getElementById("btn-overview");
    const btnAutoplay = document.getElementById("btn-autoplay");
    const transcriptPanel = document.getElementById("transcript-panel");
    const transcriptContent = document.getElementById("transcript-content");
    const videoOverlay = document.getElementById("video-overlay");
    const overlayTitle = document.getElementById("overlay-title");
    const overlayDescription = document.getElementById("overlay-description");
    const overlayReplay = document.getElementById("overlay-replay");
    const overlayNext = document.getElementById("overlay-next");
    const progressBar = document.getElementById("tour-progress-bar");
    const progressSteps = document.getElementById("tour-progress-steps");

    // ── Initialization ─────────────────────────────────────────────────
    function init() {
        buildOverviewGrid();
        buildChapterList();
        buildProgressSteps();
        bindEvents();
        handleHashNavigation();
    }

    // ── Overview Grid ──────────────────────────────────────────────────
    function buildOverviewGrid() {
        overviewGrid.innerHTML = "";
        TOUR_CHAPTERS.forEach(function (chapter, index) {
            const card = document.createElement("div");
            card.className = "overview-card";
            card.setAttribute("tabindex", "0");
            card.setAttribute("role", "button");
            card.setAttribute("aria-label", "Play: " + chapter.title);
            card.dataset.index = index;

            card.innerHTML =
                '<div class="card-icon">' + chapter.icon + '</div>' +
                '<div class="card-body">' +
                    '<h3 class="card-title">' + chapter.title + '</h3>' +
                    '<p class="card-description">' + chapter.description + '</p>' +
                    '<span class="card-badge">Chapter ' + (index + 1) + ' of ' + TOUR_CHAPTERS.length + '</span>' +
                '</div>' +
                '<div class="card-play">' +
                    '<svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor"><polygon points="5,3 19,12 5,21"/></svg>' +
                '</div>';

            card.addEventListener("click", function () {
                autoplayMode = false;
                navigateTo(index);
            });
            card.addEventListener("keydown", function (e) {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    autoplayMode = false;
                    navigateTo(index);
                }
            });

            overviewGrid.appendChild(card);
        });
    }

    // ── Chapter Sidebar ────────────────────────────────────────────────
    function buildChapterList() {
        chapterList.innerHTML = "";
        TOUR_CHAPTERS.forEach(function (chapter, index) {
            const li = document.createElement("li");
            li.className = "chapter-item";
            li.dataset.index = index;
            li.setAttribute("tabindex", "0");
            li.innerHTML =
                '<span class="chapter-icon">' + chapter.icon + '</span>' +
                '<span class="chapter-name">' + chapter.title + '</span>';

            li.addEventListener("click", function () {
                autoplayMode = false;
                navigateTo(index);
            });
            li.addEventListener("keydown", function (e) {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    autoplayMode = false;
                    navigateTo(index);
                }
            });

            chapterList.appendChild(li);
        });
    }

    // ── Progress Steps ─────────────────────────────────────────────────
    function buildProgressSteps() {
        progressSteps.innerHTML = "";
        TOUR_CHAPTERS.forEach(function (chapter, index) {
            const step = document.createElement("button");
            step.className = "progress-step";
            step.title = chapter.title;
            step.dataset.index = index;
            step.addEventListener("click", function () {
                autoplayMode = false;
                navigateTo(index);
            });
            progressSteps.appendChild(step);
        });
    }

    function updateProgress() {
        var pct = TOUR_CHAPTERS.length > 1
            ? ((currentIndex) / (TOUR_CHAPTERS.length - 1)) * 100
            : 100;
        progressBar.style.width = pct + "%";

        var steps = progressSteps.querySelectorAll(".progress-step");
        steps.forEach(function (step, i) {
            step.classList.toggle("active", i === currentIndex);
            step.classList.toggle("completed", i < currentIndex);
        });
    }

    // ── Navigation ─────────────────────────────────────────────────────
    function navigateTo(index) {
        if (index < 0 || index >= TOUR_CHAPTERS.length) return;

        currentIndex = index;
        var chapter = TOUR_CHAPTERS[currentIndex];

        // Update URL hash
        history.replaceState(null, "", "#" + chapter.id);

        // Switch panels
        overviewPanel.classList.remove("active");
        playerPanel.classList.add("active");

        // Load video
        videoSource.src = chapter.file;
        video.load();
        video.play().catch(function () { /* autoplay may be blocked */ });

        // Hide overlay
        videoOverlay.classList.add("hidden");

        // Update info
        currentTitle.textContent = chapter.title;
        currentDescription.textContent = chapter.description;
        chapterIndicator.textContent = (currentIndex + 1) + " / " + TOUR_CHAPTERS.length;

        // Navigation buttons
        btnPrev.disabled = currentIndex === 0;
        btnNext.disabled = currentIndex === TOUR_CHAPTERS.length - 1;

        // Sidebar active state
        var items = chapterList.querySelectorAll(".chapter-item");
        items.forEach(function (item, i) {
            item.classList.toggle("active", i === currentIndex);
        });

        // Transcript
        loadTranscript(chapter);

        // Progress
        updateProgress();
    }

    function showOverview() {
        autoplayMode = false;
        video.pause();
        playerPanel.classList.remove("active");
        overviewPanel.classList.add("active");
        videoOverlay.classList.add("hidden");
        history.replaceState(null, "", window.location.pathname);
    }

    function handleHashNavigation() {
        var hash = window.location.hash.replace("#", "");
        if (hash) {
            var idx = TOUR_CHAPTERS.findIndex(function (c) { return c.id === hash; });
            if (idx >= 0) {
                navigateTo(idx);
                return;
            }
        }
        // Default: show overview
        overviewPanel.classList.add("active");
        playerPanel.classList.remove("active");
    }

    // ── Transcript ─────────────────────────────────────────────────────
    function loadTranscript(chapter) {
        if (chapter.transcript) {
            transcriptContent.innerHTML = '<p class="transcript-placeholder">Loading transcript…</p>';
            transcriptPanel.style.display = "";
            transcriptPanel.removeAttribute("open");

            var transcriptUrl = chapter.transcript;
            var loadingForIndex = currentIndex;

            fetch(transcriptUrl)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("HTTP " + response.status);
                    }
                    return response.text();
                })
                .then(function (mdText) {
                    // Only update if we're still on the same chapter
                    if (currentIndex !== loadingForIndex) return;

                    var html;
                    if (typeof marked !== "undefined") {
                        // marked v4+ exposes marked.parse; older versions use marked() directly
                        html = typeof marked.parse === "function"
                            ? marked.parse(mdText)
                            : marked(mdText);
                    } else {
                        // Fallback: render as plain text paragraphs
                        var paragraphs = mdText.split("\n\n").filter(function (p) {
                            return p.trim().length > 0;
                        });
                        html = paragraphs.map(function (p) {
                            return "<p>" + escapeHtml(p.trim()) + "</p>";
                        }).join("");
                    }

                    transcriptContent.innerHTML = html;
                })
                .catch(function (err) {
                    if (currentIndex !== loadingForIndex) return;
                    console.warn("Could not load transcript:", transcriptUrl, err);
                    transcriptContent.innerHTML = '<p class="transcript-placeholder">Transcript could not be loaded.</p>';
                });
        } else {
            transcriptContent.innerHTML = '<p class="transcript-placeholder">No transcript available for this video yet.</p>';
            transcriptPanel.style.display = "";
            transcriptPanel.removeAttribute("open");
        }
    }

    function escapeHtml(text) {
        var div = document.createElement("div");
        div.textContent = text;
        return div.innerHTML;
    }

    // ── Events ─────────────────────────────────────────────────────────
    function bindEvents() {
        btnOverview.addEventListener("click", showOverview);

        btnAutoplay.addEventListener("click", function () {
            autoplayMode = true;
            navigateTo(0);
        });

        btnPrev.addEventListener("click", function () {
            if (currentIndex > 0) {
                autoplayMode = false;
                navigateTo(currentIndex - 1);
            }
        });

        btnNext.addEventListener("click", function () {
            if (currentIndex < TOUR_CHAPTERS.length - 1) {
                navigateTo(currentIndex + 1);
            }
        });

        overlayReplay.addEventListener("click", function () {
            videoOverlay.classList.add("hidden");
            video.currentTime = 0;
            video.play().catch(function () {});
        });

        overlayNext.addEventListener("click", function () {
            if (currentIndex < TOUR_CHAPTERS.length - 1) {
                navigateTo(currentIndex + 1);
            } else {
                showOverview();
            }
        });

        // Video ended
        video.addEventListener("ended", function () {
            if (autoplayMode && currentIndex < TOUR_CHAPTERS.length - 1) {
                // Brief pause then auto-advance
                showEndOverlay();
                setTimeout(function () {
                    if (autoplayMode) {
                        navigateTo(currentIndex + 1);
                    }
                }, 3000);
            } else {
                showEndOverlay();
            }
        });

        // Keyboard navigation
        document.addEventListener("keydown", function (e) {
            if (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA") return;

            if (playerPanel.classList.contains("active")) {
                if (e.key === "ArrowLeft" && !btnPrev.disabled) {
                    e.preventDefault();
                    autoplayMode = false;
                    navigateTo(currentIndex - 1);
                } else if (e.key === "ArrowRight" && !btnNext.disabled) {
                    e.preventDefault();
                    navigateTo(currentIndex + 1);
                } else if (e.key === "Escape") {
                    e.preventDefault();
                    showOverview();
                }
            }
        });

        window.addEventListener("hashchange", handleHashNavigation);
    }

    function showEndOverlay() {
        var chapter = TOUR_CHAPTERS[currentIndex];
        var isLast = currentIndex === TOUR_CHAPTERS.length - 1;

        overlayTitle.textContent = "✓ " + chapter.title;
        overlayDescription.textContent = isLast
            ? "You've completed the tour! Return to the overview to revisit any chapter."
            : "Up next: " + TOUR_CHAPTERS[currentIndex + 1].title;

        overlayNext.textContent = isLast ? "Back to Overview" : "Next Chapter →";

        if (autoplayMode && !isLast) {
            overlayDescription.textContent += " (auto-advancing in 3s…)";
        }

        videoOverlay.classList.remove("hidden");
    }

    // ── Boot ───────────────────────────────────────────────────────────
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();