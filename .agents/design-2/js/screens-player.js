/* ============================================================================
   MediaNest — Player screen (full-screen now-playing)
   Routes: 'player' (online) and 'player-offline' (local).
   Redesigned: scrollable controls, queue/up-next panel, audio art + equalizer,
   YouTube-style transport with autoplay, speed, quality, resume, seek.
   ========================================================================== */

(() => {
	var MN = window.MN;

	var timeMode = 0; // 0 elapsed, 1 remaining, 2 both
	var resumeConsumedId = null; // track id we already offered "resume" for
	var currentTrackId = null;
	var controlsEl = null;
	var seekEl = null;
	var disposed = false;

	var SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];
	var QUALITIES = ["Auto", "4K", "1440p", "1080p", "720p", "480p", "360p"];

	function isAudio(track) {
		if (!track) return false;
		return (
			MN.isAudioTrack(track) ||
			track.resolution === "Audio" ||
			track.format === "audio" ||
			track.format === "audio_extracted" ||
			track.format === "audio_only"
		);
	}

	function trackKey(track) {
		if (!track) return null;
		return track.id || track.title;
	}

	/* Simulated saved position: ~30% into the track (mirrors "last watched"). */
	function resumePosMs(durationMs) {
		return Math.round(durationMs * 0.3);
	}

	function speedLabel(v) {
		return v + "x";
	}

	function timeLeftText(st) {
		var pos = st.positionMs;
		var dur = st.durationMs;
		if (timeMode === 1) {
			return "-" + MN.fmt.duration((dur - pos) / 1000);
		}
		if (timeMode === 2) {
			return (
				MN.fmt.duration(pos / 1000) + " / -" + MN.fmt.duration((dur - pos) / 1000)
			);
		}
		return MN.fmt.duration(pos / 1000);
	}

	function typeIndicator(track) {
		var audio = isAudio(track);
		return (
			'<span class="mn-tag ' +
			(audio ? "mn-tag--audio" : "mn-tag--video") +
			'">' +
			MN.icon(audio ? "music" : "video", "mn-icon--sm") +
			(audio ? " Audio" : " Video") +
			"</span>"
		);
	}

	function backButton() {
		return (
			'<button class="mn-player__back mn-icon-btn" data-act="back" title="Back" aria-label="Back">' +
			MN.icon("back") +
			"</button>"
		);
	}

	function fullscreenButton() {
		return (
			'<button class="mn-player__fullscreen mn-icon-btn" data-act="fullscreen" title="Fullscreen" aria-label="Fullscreen">' +
			MN.icon("fullscreen") +
			"</button>"
		);
	}

	function toggleFullscreen() {
		try {
			if (document.fullscreenElement) {
				if (document.exitFullscreen) {
					document.exitFullscreen().catch(() => {});
				}
			} else if (
				document.documentElement &&
				document.documentElement.requestFullscreen
			) {
				document.documentElement.requestFullscreen().catch(() => {
					MN.toast("Fullscreen (demo)", "info");
				});
			} else {
				MN.toast("Fullscreen (demo)", "info");
			}
		} catch (err) {
			MN.toast("Fullscreen (demo)", "info");
		}
	}

	/* --- Stage: video surface or audio art ------------------------------------ */
	function stageHtml(track) {
		if (isAudio(track)) {
			return (
				"" +
				'<div class="mn-player__stage mn-player__stage--audio">' +
				'<div class="mn-player__audio">' +
				'<img src="' +
				MN.esc(track.thumbnailUrl || "") +
				'" alt="" style="width:72%;max-width:320px;aspect-ratio:1/1;object-fit:cover;border-radius:20px;box-shadow:var(--mn-shadow-3)"/>' +
				'<div class="mn-player__audio-eq">' +
				'<span class="mn-eq"><span></span><span></span><span></span><span></span><span></span></span>' +
				"</div>" +
				"</div>" +
				backButton() +
				fullscreenButton() +
				"</div>"
			);
		}
		return (
			"" +
			'<div class="mn-player__stage">' +
			'<img src="' +
			MN.esc(track.thumbnailUrl || "") +
			'" alt="" />' +
			'<button class="mn-player__play-overlay" data-act="playpause" title="Play/Pause" aria-label="Play/Pause">' +
			MN.icon("play") +
			"</button>" +
			backButton() +
			fullscreenButton() +
			"</div>"
		);
	}

	/* --- Controls -------------------------------------------------------------- */
	function controlsHtml(track) {
		var st = MN.store.get();
		var rp = resumePosMs(st.durationMs);
		var showResume =
			rp > 5000 && resumeConsumedId !== trackKey(track) && st.positionMs < 5000;
		var queue = st.queue || [];
		var nextTrack =
			queue.length > 1 ? queue[(st.queueIndex + 1) % queue.length] : null;
		var isAudioTrack = isAudio(track);

		return (
			"" +
			'<div class="mn-player__controls">' +
			'<div class="mn-player__meta">' +
			'<p class="mn-player__title">' +
			MN.esc(track.title || "Untitled") +
			"</p>" +
			'<div class="mn-row mn-gap-2 mn-wrap">' +
			'<span class="mn-player__channel">' +
			MN.esc(track.channelName || "") +
			"</span>" +
			typeIndicator(track) +
			'<span class="mn-tag ' +
			(st.isLocal ? "mn-tag--success" : "mn-tag--accent") +
			'">' +
			(st.isLocal ? "Local" : "Stream") +
			"</span>" +
			"</div>" +
			"</div>" +
			'<div class="mn-player__seek" data-act="seek" role="slider" title="Seek slider" aria-label="Seek">' +
			'<div class="mn-player__seek-track">' +
			'<div class="mn-player__seek-buffer" data-seek-buffer style="width:0%"></div>' +
			'<div class="mn-player__seek-fill" data-seek-fill style="width:0%"></div>' +
			'<div class="mn-player__seek-thumb" data-seek-thumb style="left:0%"></div>' +
			"</div>" +
			"</div>" +
			'<div class="mn-player__times">' +
			'<button class="mn-player__time" data-act="time" title="Toggle time display" aria-label="Toggle time display">' +
			timeLeftText(st) +
			"</button>" +
			"<span>" +
			MN.fmt.duration(st.durationMs / 1000) +
			"</span>" +
			"</div>" +
			'<div class="mn-player__transport">' +
			'<button class="mn-icon-btn" data-act="rewind30" title="Rewind 30s" aria-label="Rewind 30s">' +
			MN.icon("rewind30") +
			"</button>" +
			'<button class="mn-icon-btn" data-act="rewind5" title="Rewind 5s" aria-label="Rewind 5s">' +
			MN.icon("rewind5") +
			"</button>" +
			'<button class="mn-icon-btn" data-act="prev" title="Previous track" aria-label="Previous">' +
			MN.icon("prev") +
			"</button>" +
			'<button class="mn-icon-btn mn-icon-btn--primary" data-act="playpause" title="Play/Pause" aria-label="Play/Pause">' +
			MN.icon(st.isPlaying ? "pause" : "play") +
			"</button>" +
			'<button class="mn-icon-btn" data-act="next" title="Next track" aria-label="Next">' +
			MN.icon("next") +
			"</button>" +
			'<button class="mn-icon-btn" data-act="forward5" title="Forward 5s" aria-label="Forward 5s">' +
			MN.icon("forward5") +
			"</button>" +
			'<button class="mn-icon-btn" data-act="forward30" title="Forward 30s" aria-label="Forward 30s">' +
			MN.icon("forward30") +
			"</button>" +
			"</div>" +
			'<div class="mn-player__options">' +
			'<button class="mn-player__option" data-act="speed" title="Playback speed">' +
			MN.icon("speed") +
			"<span data-speed-label>" +
			speedLabel(st.speed) +
			"</span></button>" +
			(isAudioTrack
				? ""
				: '<button class="mn-player__option" data-act="quality" title="Video quality">' +
					MN.icon("sliders") +
					"<span data-quality-label>" +
					MN.esc(st.quality || "Auto") +
					"</span></button>") +
			'<button class="mn-player__option" data-act="queue" title="Up Next queue">' +
			MN.icon("playlist") +
			"<span>Queue</span>" +
			"</button>" +
			"</div>" +
			'<button class="mn-player__autoplay mn-row mn-gap-2" data-act="autoplay" title="Toggle autoplay">' +
			'<span class="mn-switch' +
			(st.autoplay ? " mn-switch--on" : "") +
			'"></span>' +
			"<span>Autoplay next</span>" +
			"</button>" +
			(nextTrack
				? '<p class="mn-player__upnext">Up next: <span class="mn-player__upnext-title">' +
					MN.esc(nextTrack.title) +
					"</span></p>"
				: "") +
			(showResume
				? '<button class="mn-chip mn-chip--active" data-act="resume" title="Resume from saved position" style="margin-top:4px">' +
					MN.icon("play") +
					"Resume from " +
					MN.fmt.duration(rp / 1000) +
					"</button>"
				: "") +
			"</div>"
		);
	}

	function renderAll(el) {
		var st = MN.store.get();
		if (!st.playing) {
			MN.render(
				el,
				MN.stateView(
					"music",
					"Nothing playing",
					"Start a video or audio item to open the player.",
				),
			);
			controlsEl = null;
			seekEl = null;
			return;
		}
		var html =
			'<div class="mn-player">' +
			stageHtml(st.playing) +
			controlsHtml(st.playing) +
			"</div>";
		MN.render(el, html);
		controlsEl = MN.qs(".mn-player__controls", el);
		seekEl = MN.qs(".mn-player__seek", el);
		bindSeek();
		refreshDynamic();
	}

	function refreshDynamic() {
		if (disposed) return;
		var st = MN.store.get();
		if (!st.playing || !controlsEl) return;
		var dur = st.durationMs || 1;
		var fillPct = Math.max(0, Math.min(100, (st.positionMs / dur) * 100));
		var bufPct = Math.max(0, Math.min(100, (st.bufferedMs / dur) * 100));

		var fill = MN.qs("[data-seek-fill]", controlsEl);
		var buf = MN.qs("[data-seek-buffer]", controlsEl);
		var thumb = MN.qs("[data-seek-thumb]", controlsEl);
		if (fill) fill.style.width = fillPct + "%";
		if (buf) buf.style.width = bufPct + "%";
		if (thumb) thumb.style.left = fillPct + "%";

		var timeLeft = MN.qs("[data-time-left]", controlsEl);
		var timeBtn = MN.qs('[data-act="time"]', controlsEl);
		if (timeBtn) timeBtn.textContent = timeLeftText(st);
		if (timeLeft) timeLeft.textContent = timeLeftText(st);

		var pp = MN.qs('[data-act="playpause"]', controlsEl);
		if (pp && !pp.classList.contains("mn-player__play-overlay")) {
			MN.render(pp, MN.icon(st.isPlaying ? "pause" : "play"));
		}

		// overlay play/pause button on the video stage
		var overlay = MN.qs(
			".mn-player__play-overlay",
			MN.qs(".mn-player", controlsEl.parentElement) || controlsEl,
		);
		if (overlay) {
			MN.render(overlay, MN.icon(st.isPlaying ? "pause" : "play"));
			overlay.style.opacity = st.isPlaying ? "0" : "1";
		}

		var speedLabelEl = MN.qs("[data-speed-label]", controlsEl);
		if (speedLabelEl) speedLabelEl.textContent = speedLabel(st.speed);

		var qualityLabelEl = MN.qs("[data-quality-label]", controlsEl);
		if (qualityLabelEl) qualityLabelEl.textContent = st.quality || "Auto";

		var sw = MN.qs(".mn-switch", controlsEl);
		if (sw) sw.classList.toggle("mn-switch--on", !!st.autoplay);

		var upnext = MN.qs(".mn-player__upnext-title", controlsEl);
		var queue = st.queue || [];
		var nextTrack =
			queue.length > 1 ? queue[(st.queueIndex + 1) % queue.length] : null;
		if (upnext) {
			if (nextTrack) upnext.textContent = nextTrack.title;
		}
	}

	function bindSeek() {
		if (!seekEl) return;
		var dragging = false;
		function ratioFromEvent(e) {
			var rect = seekEl.getBoundingClientRect();
			var x = e.clientX - rect.left;
			return Math.max(0, Math.min(1, x / rect.width));
		}
		function doSeek(e) {
			var st = MN.store.get();
			if (!st.playing || st.durationMs <= 0) return;
			MN.playback.seek(ratioFromEvent(e) * st.durationMs);
			refreshDynamic();
		}
		seekEl.addEventListener("pointerdown", (e) => {
			dragging = true;
			try {
				seekEl.setPointerCapture(e.pointerId);
			} catch (err) {}
			doSeek(e);
		});
		seekEl.addEventListener("pointermove", (e) => {
			if (dragging) doSeek(e);
		});
		function endDrag() {
			dragging = false;
		}
		seekEl.addEventListener("pointerup", endDrag);
		seekEl.addEventListener("pointercancel", endDrag);
	}

	/* --- Queue / up-next sheet ------------------------------------------------ */
	function openQueueSheet() {
		var st = MN.store.get();
		var queue = st.queue || [];
		if (queue.length === 0) {
			MN.toast("Nothing in the queue", "info");
			return;
		}

		function renderQueueSheetBody(bodyEl) {
			var currentSt = MN.store.get();
			var q = currentSt.queue || [];
			var curIdx = currentSt.queueIndex;

			var headerHtml =
				'<div class="mn-player__queue-header mn-row mn-between" style="padding:0 4px 12px">' +
				'<span class="mn-key">Up Next</span>' +
				'<span class="mn-muted" style="font-size:12px">' +
				q.length +
				" items</span>" +
				"</div>";

			var html = headerHtml + '<div class="mn-list mn-queue-list">';
			q.forEach((t, i) => {
				var active = i === curIdx;
				var audio = isAudio(t);
				html +=
					'<div class="mn-player__queue-item' +
					(active ? " mn-player__queue-item--active" : "") +
					'" data-index="' +
					i +
					'" draggable="true">' +
					'<span class="mn-queue__handle" title="Drag to reorder">' +
					MN.icon("grip") +
					"</span>" +
					'<span class="mn-queue__index">' +
					(i + 1) +
					"</span>" +
					'<div class="mn-thumb" style="width:52px;border-radius:8px">' +
					'<img src="' +
					MN.esc(t.thumbnailUrl || "") +
					'" alt=""/>' +
					MN.typeBadge(t) +
					"</div>" +
					'<div class="mn-stack mn-fill" style="min-width:0;gap:2px">' +
					'<span class="mn-media-row__title" style="-webkit-line-clamp:1">' +
					MN.esc(t.title || "Untitled") +
					"</span>" +
					'<span class="mn-media-row__meta">' +
					(active
						? '<span class="mn-queue__now">' +
							MN.icon("play", "mn-icon--sm mn-accent") +
							" Now playing</span>"
						: MN.icon(audio ? "music" : "video", "mn-icon--sm") +
							" " +
							MN.esc(t.channelName || "")) +
					"</span>" +
					"</div>" +
					(active
						? '<span class="mn-muted" style="font-size:12px" title="Currently playing">' +
							MN.icon("pause", "mn-icon--sm mn-accent") +
							"</span>"
						: '<button class="mn-icon-btn mn-icon-btn--sm" data-act="play-queue" data-index="' +
							i +
							'" title="Play track">' +
							MN.icon("play") +
							"</button>") +
					"</div>";
			});
			html += "</div>";

			MN.render(bodyEl, html);
			bindQueueEvents(bodyEl);
		}

		function bindQueueEvents(bodyEl) {
			var draggedIdx = null;

			bodyEl.querySelectorAll(".mn-player__queue-item").forEach((itemEl) => {
				itemEl.addEventListener("dragstart", (e) => {
					draggedIdx = parseInt(itemEl.getAttribute("data-index"), 10);
					e.dataTransfer.effectAllowed = "move";
					e.dataTransfer.setData("text/plain", "" + draggedIdx);
					itemEl.classList.add("mn-queue-item--dragging");
				});

				itemEl.addEventListener("dragend", () => {
					itemEl.classList.remove("mn-queue-item--dragging");
					bodyEl.querySelectorAll(".mn-player__queue-item").forEach((el) => {
						el.classList.remove("mn-queue-item--drag-over");
					});
				});

				itemEl.addEventListener("dragover", (e) => {
					e.preventDefault();
					e.dataTransfer.dropEffect = "move";
					itemEl.classList.add("mn-queue-item--drag-over");
				});

				itemEl.addEventListener("dragleave", () => {
					itemEl.classList.remove("mn-queue-item--drag-over");
				});

				itemEl.addEventListener("drop", (e) => {
					e.preventDefault();
					itemEl.classList.remove("mn-queue-item--drag-over");
					var targetIdx = parseInt(itemEl.getAttribute("data-index"), 10);
					if (
						draggedIdx !== null &&
						!isNaN(draggedIdx) &&
						!isNaN(targetIdx) &&
						draggedIdx !== targetIdx
					) {
						reorderQueueItem(draggedIdx, targetIdx);
						renderQueueSheetBody(bodyEl);
					}
				});

				itemEl.addEventListener("click", (e) => {
					if (e.target.closest(".mn-queue__handle")) return;
					var idx = parseInt(itemEl.getAttribute("data-index"), 10);
					if (!isNaN(idx)) {
						MN.playback.play(MN.store.get().queue, idx);
						MN.closeSheet();
					}
				});
			});
		}

		function reorderQueueItem(fromIdx, toIdx) {
			var curSt = MN.store.get();
			var q = (curSt.queue || []).slice();
			if (fromIdx < 0 || fromIdx >= q.length || toIdx < 0 || toIdx >= q.length)
				return;
			var playingTrack = q[curSt.queueIndex];
			var moved = q.splice(fromIdx, 1)[0];
			q.splice(toIdx, 0, moved);

			var newQueueIndex = q.findIndex((t) => t === playingTrack);
			if (newQueueIndex < 0 && playingTrack) {
				newQueueIndex = q.findIndex((t) => trackKey(t) === trackKey(playingTrack));
			}
			if (newQueueIndex < 0) newQueueIndex = curSt.queueIndex;

			MN.store.set({
				queue: q,
				queueIndex: newQueueIndex,
			});
			MN.toast("Queue reordered", "info");
		}

		MN.sheet({
			title: "Up Next",
			body: '<div id="queue-sheet-content"></div>',
			onOpen: (bodyEl) => {
				renderQueueSheetBody(bodyEl);
			},
		});
	}

	function openSpeedSheet() {
		var st = MN.store.get();
		var rows = SPEEDS.map((v) => {
			var active = st.speed === v;
			return (
				'<button class="mn-select__option' +
				(active ? " mn-select__option--active" : "") +
				'" data-v="' +
				v +
				'" title="Speed ' +
				speedLabel(v) +
				'">' +
				"<span>" +
				speedLabel(v) +
				"</span>" +
				(active ? MN.icon("check") : "") +
				"</button>"
			);
		}).join("");
		MN.sheet({
			title: "Playback speed",
			body: rows,
			onOpen: (body) => {
				body.querySelectorAll("[data-v]").forEach((b) => {
					b.onclick = () => {
						MN.playback.setSpeed(parseFloat(b.getAttribute("data-v")));
						MN.closeSheet();
						MN.toast(
							"Speed set to " + speedLabel(parseFloat(b.getAttribute("data-v"))),
							"info",
						);
					};
				});
			},
		});
	}

	function openQualitySheet() {
		var st = MN.store.get();
		var opts = QUALITIES.slice();
		if (
			st.playing &&
			st.playing.resolution &&
			!opts.includes(st.playing.resolution)
		) {
			opts.unshift(st.playing.resolution);
		}
		var qrows = opts
			.map((q) => {
				var active = (st.quality || "Auto") === q;
				return (
					'<button class="mn-select__option' +
					(active ? " mn-select__option--active" : "") +
					'" data-q="' +
					MN.esc(q) +
					'" title="Quality ' +
					MN.esc(q) +
					'">' +
					"<span>" +
					MN.esc(q) +
					"</span>" +
					(active ? MN.icon("check") : "") +
					"</button>"
				);
			})
			.join("");
		MN.sheet({
			title: "Video quality",
			body: qrows,
			onOpen: (body) => {
				body.querySelectorAll("[data-q]").forEach((b) => {
					b.onclick = () => {
						MN.playback.setQuality(b.getAttribute("data-q"));
						MN.closeSheet();
						MN.toast("Quality set to " + b.getAttribute("data-q"), "info");
					};
				});
			},
		});
	}

	function bindControls(el) {
		if (!controlsEl) return;
		controlsEl.addEventListener("click", (e) => {
			var btn = e.target.closest("[data-act]");
			if (!btn) return;
			var act = btn.getAttribute("data-act");
			var st = MN.store.get();

			if (act === "back") {
				MN.router.back();
				return;
			}
			if (act === "fullscreen") {
				toggleFullscreen();
				return;
			}
			if (act === "playpause") {
				MN.playback.toggle();
				return;
			}
			if (act === "next") {
				MN.playback.next();
				return;
			}
			if (act === "prev") {
				MN.playback.prev();
				return;
			}
			if (act === "rewind5") {
				MN.playback.seekRelative(-5000);
				return;
			}
			if (act === "forward5") {
				MN.playback.seekRelative(5000);
				return;
			}
			if (act === "rewind30") {
				MN.playback.seekRelative(-30000);
				return;
			}
			if (act === "forward30") {
				MN.playback.seekRelative(30000);
				return;
			}
			if (act === "autoplay") {
				MN.playback.setAutoplay(!st.autoplay);
				MN.toast(st.autoplay ? "Autoplay off" : "Autoplay on", "info");
				return;
			}
			if (act === "time") {
				timeMode = (timeMode + 1) % 3;
				refreshDynamic();
				return;
			}
			if (act === "speed") {
				openSpeedSheet();
				return;
			}
			if (act === "quality") {
				openQualitySheet();
				return;
			}
			if (act === "queue") {
				openQueueSheet();
				return;
			}
			if (act === "resume") {
				var rp = resumePosMs(st.durationMs);
				resumeConsumedId = trackKey(st.playing);
				MN.playback.seek(rp);
				MN.toast("Resumed from " + MN.fmt.duration(rp / 1000), "info");
				refreshDynamic();
				return;
			}
		});
	}

	/* Bind the video-stage overlay button (outside the scrollable controls). */
	function bindStage(el) {
		var stage = MN.qs(".mn-player__stage", el);
		if (!stage) return;
		stage.onclick = (e) => {
			var btn = e.target.closest("[data-act]");
			if (!btn) return;
			var act = btn.getAttribute("data-act");
			if (act === "back") {
				MN.router.back();
				return;
			}
			if (act === "fullscreen") {
				toggleFullscreen();
				return;
			}
			if (act === "playpause") {
				MN.playback.toggle();
				return;
			}
		};
	}

	function refresh() {
		if (disposed) return;
		var st = MN.store.get();
		if (!st.playing) {
			renderAll(MN.qs("#view-root"));
			return;
		}
		var key = trackKey(st.playing);
		if (key !== currentTrackId) {
			currentTrackId = key;
			renderAll(MN.qs("#view-root"));
			return;
		}
		refreshDynamic();
	}

	function mount(el) {
		disposed = false;
		currentTrackId = null;
		timeMode = 0;
		var st = MN.store.get();
		currentTrackId = trackKey(st.playing);
		renderAll(el);
		bindControls(el);
		bindStage(el);
		MN.store.subscribe(refresh);
		return {
			unmount: () => {
				disposed = true;
			},
		};
	}

	var def = { title: "Now Playing", appbar: false, mount: mount };
	MN.router.register("player", def);
	MN.router.register("player-offline", def);
})();
