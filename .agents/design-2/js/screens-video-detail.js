/* ============================================================================
   MediaNest — Video Detail screen
   Route: video-detail/:id
   Reached by clicking a video title/metadata from Home or Collections.
   Shows hero, metadata, actions, description, per-video stats, and streams.
   ========================================================================== */

(() => {
	var MN = window.MN;
	var DATA = MN.DATA;

	/* Resolve a video by id; fall back to the first video so the screen never
     appears blank when a stale or missing id is passed. */
	function findVideo(id) {
		for (var i = 0; i < DATA.videos.length; i++) {
			if (DATA.videos[i].id === id) return DATA.videos[i];
		}
		return DATA.videos[0];
	}

	function isSubscribed(video) {
		return DATA.subscriptions.some(
			(s) =>
				s.sourceType === "channel" &&
				(s.sourceId === video.channelId || s.name === video.channelName),
		);
	}

	function fmtDate(ts) {
		if (!ts) return "";
		var d = new Date(ts);
		return d.toLocaleDateString(undefined, {
			year: "numeric",
			month: "short",
			day: "numeric",
		});
	}

	/* Parse a resolution label ('1080p', '4K', 'Audio') into a numeric max. */
	function parseResolution(res) {
		var s = String(res || "").toLowerCase();
		if (s.includes("4k") || s.includes("2160")) return 2160;
		if (s.includes("1440")) return 1440;
		var n = parseInt(s, 10);
		return n || 0;
	}

	function buildStreams(video) {
		var res = parseResolution(video.resolution);
		if (res === 0) return [];
		var ladder = [];
		if (res >= 2160) ladder = ["2160p", "1440p", "1080p", "720p", "480p", "360p"];
		else if (res >= 1440) ladder = ["1440p", "1080p", "720p", "480p", "360p"];
		else if (res >= 1080) ladder = ["1080p", "720p", "480p", "360p"];
		else if (res >= 720) ladder = ["720p", "480p", "360p"];
		else ladder = ["480p", "360p", "240p"];

		var bitrates = {
			"2160p": 45000000,
			"1440p": 16000000,
			"1080p": 8000000,
			"720p": 5000000,
			"480p": 2500000,
			"360p": 1000000,
			"240p": 500000,
		};
		var codecs = ["vp9", "avc1"];
		var dur = video.durationSeconds || 300;
		var out = [];

		ladder.forEach((r) => {
			codecs.forEach((codec, ci) => {
				var bps = bitrates[r] || 2500000;
				if (ci === 1) bps = Math.round(bps * 1.4);
				out.push({
					format: "video",
					quality: r,
					codec: codec,
					sizeBytes: Math.round((bps / 8) * dur),
				});
			});
		});
		return out;
	}

	function buildAudioStreams(video) {
		var dur = video.durationSeconds || 300;
		return [
			{
				format: "audio",
				quality: "128kbps",
				codec: "opus",
				sizeBytes: Math.round((128000 / 8) * dur),
			},
			{
				format: "audio",
				quality: "256kbps",
				codec: "m4a",
				sizeBytes: Math.round((256000 / 8) * dur),
			},
		];
	}

	function statRow(iconName, label, value) {
		return (
			'<div class="mn-row mn-gap-2" style="padding:8px 0">' +
			'<span style="width:20px;color:var(--mn-accent)">' +
			MN.icon(iconName) +
			"</span>" +
			'<span class="mn-muted mn-fill">' +
			MN.esc(label) +
			"</span>" +
			'<span class="mn-num">' +
			MN.esc(value) +
			"</span>" +
			"</div>"
		);
	}

	function streamRowHtml(idx, s) {
		var isAudio = s.format === "audio";
		var sub = isAudio ? s.quality : s.codec.toUpperCase();
		var size = "~" + MN.fmt.bytes(s.sizeBytes);
		return (
			'<div class="mn-media-row mn-stream-row" style="padding:8px 12px;gap:10px;margin-bottom:6px;align-items:center;min-height:52px">' +
			'<span style="width:34px;height:34px;border-radius:8px;background:var(--mn-card);border:1px solid var(--mn-border);display:flex;align-items:center;justify-content:center;color:var(--mn-accent);flex:0 0 auto">' +
			MN.icon(isAudio ? "music" : "video") +
			"</span>" +
			'<div class="mn-media-row__body" style="gap:0px;min-width:0">' +
			'<p class="mn-media-row__title" style="margin:0;font-size:13px;font-weight:600;-webkit-line-clamp:1">' +
			MN.esc(sub) +
			"</p>" +
			'<p class="mn-media-row__meta" style="margin:1px 0 0;font-size:11px">' +
			MN.esc(size) +
			"</p>" +
			"</div>" +
			'<div class="mn-media-row__actions" style="margin-left:auto;display:flex;align-items:center;gap:2px">' +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-act="play-stream" data-sidx="' +
			idx +
			'" aria-label="Play" title="Play stream">' +
			MN.icon("play") +
			"</button>" +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-act="download-stream" data-sidx="' +
			idx +
			'" aria-label="Download" title="Download stream">' +
			MN.icon("download") +
			"</button>" +
			"</div>" +
			"</div>"
		);
	}

	MN.router.register("video-detail/:id", {
		title: "Video",
		back: true,
		mount: (el, params) => {
			var video = findVideo(params && params.id);
			var descExpanded = false;
			var sessionsExpanded = false;
			var titleExpanded = false; // click the title to toggle full vs clamped

			function playVideo(quality) {
				var queue = DATA.videos.map((v) => ({
					id: v.id,
					title: v.title,
					channelName: v.channelName,
					durationSeconds: v.durationSeconds,
					thumbnailUrl: v.thumbnailUrl,
					resolution: v.resolution,
					isLocal: !!v.localFilePath,
				}));
				var idx = DATA.videos.indexOf(video);
				if (idx < 0) idx = 0;
				if (quality) MN.playback.setQuality(quality);
				MN.playback.play(queue, idx);
				MN.router.navigate("player");
			}

			function openWatchDialog() {
				var current = video.watchCount || 0;
				MN.dialog({
					title: "Set Watched",
					body:
						'<div class="mn-field" style="margin-bottom:10px">' +
						'<input type="number" min="0" inputmode="numeric" data-watch="' +
						current +
						'" value="' +
						current +
						'"/>' +
						"</div>" +
						'<p class="mn-muted">Number of times you have watched this video.</p>',
					actions: [
						{ label: "Cancel", cls: "mn-btn--ghost" },
						{
							label: "Save",
							cls: "mn-btn--primary",
							onClick: () => {
								var input = MN.qs("#dialog [data-watch]");
								var n = parseInt(input && input.value, 10);
								if (!Number.isNaN(n) && n >= 0) {
									video.watchCount = n;
									MN.toast("Watch count updated", "success");
									render();
								}
							},
						},
					],
				});
			}

			function render() {
				var html = "";
				var hist = null;
				var sessions = [];
				var i;

				for (i = 0; i < DATA.history.length; i++) {
					if (DATA.history[i].videoId === video.id) {
						hist = DATA.history[i];
						break;
					}
				}
				for (i = 0; i < DATA.watchSessions.length; i++) {
					if (DATA.watchSessions[i].videoId === video.id) {
						sessions.push(DATA.watchSessions[i]);
					}
				}

				var subscribed = isSubscribed(video);
				var isAudio = MN.isAudioTrack(video);

				/* --- Hero --- */
				html += '<div class="mn-detail-hero">';
				html +=
					'<div class="mn-thumb" style="border-radius:var(--mn-r-hero);aspect-ratio:16/9">';
				html += '<img src="' + MN.esc(video.thumbnailUrl) + '" alt=""/>';
				html += MN.typeBadge(video);
				html +=
					'<button class="mn-thumb__play" style="opacity:1;background:rgba(9,5,6,0.35)" data-act="play" aria-label="Play" title="Play video">' +
					MN.icon("play") +
					"</button>";
				if (video.watchCount > 0) {
					html +=
						'<span class="mn-thumb__badge mn-thumb__badge--tr">' +
						MN.icon("eye") +
						" " +
						video.watchCount +
						"</span>";
				}
				if (hist && hist.positionMillis > 0 && video.durationSeconds > 0) {
					html +=
						'<span class="mn-thumb__badge mn-thumb__badge--bl">' +
						MN.icon("history") +
						" " +
						MN.fmt.duration(hist.positionMillis / 1000) +
						"</span>";
				}
				if (video.durationSeconds > 0) {
					html +=
						'<span class="mn-thumb__badge mn-thumb__badge--br">' +
						MN.fmt.duration(video.durationSeconds) +
						"</span>";
				}
				if (hist && hist.positionMillis > 0 && video.durationSeconds > 0) {
					var frac = Math.min(
						100,
						(hist.positionMillis / (video.durationSeconds * 1000)) * 100,
					);
					html +=
						'<div class="mn-thumb__progress"><span style="width:' +
						frac +
						'%"></span></div>';
				}
				html += "</div>";
				html += "</div>";

				/* --- Title + channel + subscribe --- */
				html +=
					'<button class="mn-detail-title" data-act="title" style="display:block;width:100%;text-align:left;padding:0;font-size:20px;font-weight:var(--mn-fw-semibold);line-height:1.3;margin:0 0 6px" title="' +
					"Tap to expand/collapse" +
					'">' +
					'<span style="' +
					(titleExpanded
						? ""
						: "display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden") +
					'">' +
					MN.esc(video.title) +
					"</span>" +
					(titleExpanded
						? ' <span class="mn-muted" style="font-size:var(--mn-fs-mini);font-weight:var(--mn-fw-medium)">' +
							MN.icon("chevron-up", "mn-icon--sm") +
							" collapse</span>"
						: ' <span class="mn-muted" style="font-size:var(--mn-fs-mini);font-weight:var(--mn-fw-medium)">' +
							MN.icon("chevron-down", "mn-icon--sm") +
							" expand</span>") +
					"</button>";
				html +=
					'<div class="mn-row mn-gap-1" style="margin:0 0 10px">' +
					MN.icon(isAudio ? "music" : "video", "mn-icon--sm mn-accent") +
					'<span class="mn-tag' +
					(isAudio ? " mn-tag--audio" : " mn-tag--video") +
					'">' +
					MN.esc(isAudio ? "Audio" : "Video") +
					"</span>" +
					'<span class="mn-tag mn-tag--accent">' +
					MN.esc(video.resolution || "Auto") +
					"</span>" +
					"</div>";
				html += '<div class="mn-row mn-between mn-gap-2">';
				html +=
					'<span class="mn-muted mn-fill mn-truncate">' +
					MN.esc(video.channelName) +
					"</span>";
				html +=
					'<button class="mn-btn ' +
					(subscribed ? "mn-btn--secondary" : "mn-btn--deep") +
					'" data-act="subscribe" title="' +
					(subscribed ? "Unsubscribe channel" : "Subscribe channel") +
					'">' +
					MN.icon(subscribed ? "check" : "channel") +
					" " +
					(subscribed ? "Subscribed" : "Subscribe") +
					"</button>";
				html += "</div>";

				/* --- Metadata --- */
				html += '<div class="mn-stack mn-gap-1" style="margin-top:12px">';
				if (video.uploadDate) {
					html +=
						'<span class="mn-muted">Released: ' +
						MN.esc(video.uploadDate) +
						"</span>";
				}
				var pubViewsText = video.publicViews
					? (typeof video.publicViews === "number"
							? video.publicViews.toLocaleString()
							: video.publicViews) + " views"
					: "—";
				html +=
					'<span class="mn-row mn-gap-1 mn-muted">' +
					MN.icon("eye") +
					" Public views: " +
					MN.esc(pubViewsText) +
					"</span>";
				var completedDls = DATA.downloads.filter(
					(d) => d.videoId === video.id && d.status === "COMPLETED",
				);
				if (completedDls.length > 0) {
					html +=
						'<div class="mn-stack mn-gap-1" style="margin-top:2px">' +
						'<span class="mn-row mn-gap-1">' +
						MN.icon("check-circle") +
						' <span class="mn-success">Downloaded versions:</span></span>';
					completedDls.forEach((cdl) => {
						var isAud = cdl.format === "audio" || cdl.format === "audio_extracted";
						var qLabel = isAud ? "Audio · " + cdl.quality : cdl.quality;
						html +=
							'<span class="mn-muted" style="margin-left:22px;font-size:var(--mn-fs-meta)">• ' +
							MN.esc(qLabel) +
							" (" +
							MN.fmt.bytes(cdl.fileSizeBytes) +
							") — " +
							fmtDate(cdl.downloadedAt || video.downloadedAt) +
							"</span>";
					});
					html += "</div>";
				} else {
					html +=
						'<span class="mn-muted" style="font-size:var(--mn-fs-meta)">Not downloaded</span>';
				}
				html += "</div>";

				/* --- Action row --- */
				html += '<div class="mn-row mn-gap-2" style="margin-top:14px">';
				html +=
					'<button class="mn-btn mn-btn--deep mn-fill" data-act="youtube" title="Open on YouTube">' +
					MN.icon("youtube") +
					" YouTube</button>";
				html +=
					'<button class="mn-btn mn-btn--secondary mn-fill" data-act="watch" title="Set watch count">' +
					MN.icon("eye") +
					" Set Watched</button>";
				html += "</div>";

				/* --- Description --- */
				if (video.description) {
					html += '<div class="mn-card mn-card--pad" style="margin-top:14px">';
					html += '<p class="mn-key" style="margin:0 0 6px">Description</p>';
					html +=
						'<p class="mn-muted" data-desc style="margin:0;' +
						(descExpanded
							? ""
							: "display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden") +
						'">' +
						MN.esc(video.description) +
						"</p>";
					html +=
						'<button class="mn-btn mn-btn--ghost" data-act="desc" style="margin-top:8px;padding-left:0" title="Toggle description">' +
						(descExpanded ? "Show less" : "Show more") +
						" " +
						MN.icon(descExpanded ? "chevron-up" : "chevron-down") +
						"</button>";
					html += "</div>";
				}

				/* --- Your Statistics --- */
				html +=
					'<div class="mn-section-title" style="margin-top:16px"><h2>Your Statistics</h2></div>';
				html += '<div class="mn-card mn-card--pad">';
				html += statRow("eye", "Your watch count", String(video.watchCount));
				if (hist && hist.positionMillis > 0) {
					html += statRow(
						"history",
						"Last watch position",
						MN.fmt.duration(hist.positionMillis / 1000),
					);
				}
				html += statRow(
					"history",
					"Total watch time",
					hist ? MN.fmt.duration(Math.round(hist.totalWatchTimeMillis / 1000)) : "—",
				);
				html += statRow(
					"history",
					"Last watched",
					hist && hist.playedAt ? fmtDate(hist.playedAt) : "—",
				);
				html += statRow(
					"play",
					"Play count",
					sessions.length > 0 ? String(sessions.length) : "0",
				);

				if (sessions.length > 0) {
					html +=
						'<button class="mn-btn mn-btn--ghost" data-act="sessions" style="padding-left:0" title="Toggle watch history log">' +
						(sessionsExpanded ? "Hide" : "View") +
						" watch history log " +
						MN.icon(sessionsExpanded ? "chevron-up" : "chevron-down") +
						"</button>";
					if (sessionsExpanded) {
						var ordered = sessions.slice().sort((a, b) => b.watchedAt - a.watchedAt);
						html += '<div class="mn-stack mn-gap-1" style="margin-top:6px">';
						ordered.forEach((s) => {
							html +=
								'<span class="mn-muted">• Watched on ' +
								fmtDate(s.watchedAt) +
								"</span>";
						});
						html += "</div>";
					}
				}
				html += "</div>";

				/* --- Downloads for this video --- */
				var dlRows = DATA.downloads.filter((d) => d.videoId === video.id);
				html +=
					'<div class="mn-section-title" style="margin-top:16px"><h2>Downloads</h2></div>';
				if (dlRows.length === 0) {
					html += MN.stateView(
						"download",
						"No downloads yet",
						"Download a stream below and it will appear here.",
					);
				} else {
					html += '<div class="mn-list">';
					dlRows.forEach((dl) => {
						var isAudioDl = dl.format === "audio" || dl.format === "audio_extracted";
						var isCancelable =
							dl.status === "QUEUED" ||
							dl.status === "DOWNLOADING" ||
							dl.status === "PAUSED";
						var cancelBtn = isCancelable
							? '<button class="mn-icon-btn mn-icon-btn--sm" data-cancel="' +
								dl.id +
								'" title="Cancel download" aria-label="Cancel download">' +
								MN.icon("close") +
								"</button>"
							: "";
						var statusIcon = "";
						if (dl.status === "COMPLETED") {
							statusIcon =
								'<span class="mn-icon-btn mn-icon-btn--xs mn-success" title="Status: Completed" data-act="status-toast" data-msg="Status: Completed">' +
								MN.icon("check-circle", "mn-icon--sm mn-success") +
								"</span>";
						} else if (dl.status === "FAILED") {
							statusIcon =
								'<span class="mn-icon-btn mn-icon-btn--xs mn-error" title="Status: Failed" data-act="status-toast" data-msg="Status: Failed — ' +
								MN.esc(dl.errorMessage || "") +
								'">' +
								MN.icon("warning", "mn-icon--sm mn-error") +
								"</span>";
						} else if (dl.status === "DOWNLOADING") {
							statusIcon =
								'<span class="mn-icon-btn mn-icon-btn--xs mn-accent" title="Status: Downloading" data-act="status-toast" data-msg="Status: Downloading">' +
								MN.icon("download", "mn-icon--sm mn-accent") +
								"</span>";
						} else if (dl.status === "PAUSED") {
							statusIcon =
								'<span class="mn-icon-btn mn-icon-btn--xs" title="Status: Paused" data-act="status-toast" data-msg="Status: Paused">' +
								MN.icon("pause", "mn-icon--sm") +
								"</span>";
						} else if (dl.status === "QUEUED") {
							statusIcon =
								'<span class="mn-icon-btn mn-icon-btn--xs mn-muted" title="Status: Queued" data-act="status-toast" data-msg="Status: Queued">' +
								MN.icon("history", "mn-icon--sm") +
								"</span>";
						}

						html +=
							'<div class="mn-media-row mn-stream-row" style="padding:8px 12px;gap:10px;margin-bottom:6px;align-items:center;min-height:52px">' +
							'<span style="width:34px;height:34px;border-radius:8px;background:var(--mn-card);border:1px solid var(--mn-border);display:flex;align-items:center;justify-content:center;color:var(--mn-accent);flex:0 0 auto">' +
							MN.icon(isAudioDl ? "music" : "video") +
							"</span>" +
							'<div class="mn-media-row__body" style="gap:0px;min-width:0">' +
							'<p class="mn-media-row__title" style="margin:0;font-size:13px;font-weight:600;-webkit-line-clamp:1">' +
							MN.esc(isAudioDl ? "Audio" : "Video") +
							" · " +
							MN.esc(dl.quality) +
							"</p>" +
							'<p class="mn-media-row__meta" style="margin:1px 0 0;font-size:11px">' +
							MN.fmt.bytes(dl.fileSizeBytes) +
							"</p>" +
							"</div>" +
							'<div class="mn-media-row__actions" style="margin-left:auto;display:flex;align-items:center;gap:2px">' +
							statusIcon +
							cancelBtn +
							"</div>" +
							"</div>";
					});
					html += "</div>";
				}

				/* --- Available streams --- */
				var vStreams = buildStreams(video);
				var aStreams = buildAudioStreams(video);

				html +=
					'<div class="mn-section-title" style="margin-top:16px"><h2>Available Streams</h2></div>';

				if (vStreams.length === 0 && aStreams.length === 0) {
					html += MN.stateView(
						"warning",
						"No streams",
						"No playable streams are available for this video.",
					);
				}

				var streamRefs = [];
				var sidx = 0;
				var seenRes = [];

				vStreams.forEach((s) => {
					if (!seenRes.includes(s.quality)) {
						seenRes.push(s.quality);
						html +=
							'<p class="mn-key" style="margin:10px 0 4px">' +
							MN.esc(s.quality) +
							"</p>";
					}
					streamRefs[sidx] = s;
					html += streamRowHtml(sidx, s);
					sidx++;
				});

				if (aStreams.length > 0) {
					html += '<p class="mn-key" style="margin:14px 0 4px">Audio Only</p>';
					aStreams.forEach((s) => {
						streamRefs[sidx] = s;
						html += streamRowHtml(sidx, s);
						sidx++;
					});
				}

				MN.render(el, html);

				/* --- Wire events --- */
				var playBtn = el.querySelector('[data-act="play"]');
				if (playBtn) {
					playBtn.onclick = () => {
						playVideo();
					};
				}

				var subBtn = el.querySelector('[data-act="subscribe"]');
				if (subBtn) {
					subBtn.onclick = () => {
						var idx = -1;
						for (var k = 0; k < DATA.subscriptions.length; k++) {
							var s = DATA.subscriptions[k];
							if (
								s.sourceType === "channel" &&
								(s.sourceId === video.channelId || s.name === video.channelName)
							) {
								idx = k;
								break;
							}
						}
						if (idx >= 0) {
							DATA.subscriptions.splice(idx, 1);
							MN.toast("Unsubscribed from " + video.channelName, "info");
						} else {
							DATA.subscriptions.push({
								id: Date.now(),
								sourceType: "channel",
								sourceId: video.channelId,
								name: video.channelName,
								thumbnailUrl: video.thumbnailUrl,
								autoDownload: false,
								audioOnly: false,
							});
							MN.toast("Subscribed to " + video.channelName, "success");
						}
						render();
					};
				}

				var ytBtn = el.querySelector('[data-act="youtube"]');
				if (ytBtn) {
					ytBtn.onclick = () => {
						MN.toast("Opening on YouTube…", "info");
					};
				}

				var watchBtn = el.querySelector('[data-act="watch"]');
				if (watchBtn) watchBtn.onclick = openWatchDialog;

				var descBtn = el.querySelector('[data-act="desc"]');
				if (descBtn) {
					descBtn.onclick = () => {
						descExpanded = !descExpanded;
						render();
					};
				}

				var titleBtn = el.querySelector('[data-act="title"]');
				if (titleBtn) {
					titleBtn.onclick = () => {
						titleExpanded = !titleExpanded;
						render();
					};
				}

				var sessBtn = el.querySelector('[data-act="sessions"]');
				if (sessBtn) {
					sessBtn.onclick = () => {
						sessionsExpanded = !sessionsExpanded;
						render();
					};
				}

				el.querySelectorAll('[data-act="status-toast"]').forEach((btn) => {
					btn.onclick = (e) => {
						e.stopPropagation();
						var msg = btn.getAttribute("data-msg") || "Status info";
						MN.toast(msg, "info");
					};
				});

				el.querySelectorAll("[data-cancel]").forEach((btn) => {
					btn.onclick = (e) => {
						e.stopPropagation();
						var dlId = parseInt(btn.getAttribute("data-cancel"), 10);
						var dl = DATA.downloads.find((x) => x.id === dlId);
						if (dl) {
							MN.downloads.cancel(dl);
							render();
						}
					};
				});

				el.querySelectorAll('[data-act="play-stream"]').forEach((btn) => {
					btn.onclick = () => {
						var s = streamRefs[Number(btn.getAttribute("data-sidx"))];
						if (!s) return;
						playVideo(s.quality);
					};
				});

				el.querySelectorAll('[data-act="download-stream"]').forEach((btn) => {
					btn.onclick = () => {
						var s = streamRefs[Number(btn.getAttribute("data-sidx"))];
						if (!s) return;
						var label =
							s.format === "audio" ? s.quality : s.quality + " (" + s.codec + ")";
						MN.downloads.enqueue(video, s.format, label);
					};
				});
			}

			render();
			return {
				unmount: () => {},
			};
		},
	});
})();
