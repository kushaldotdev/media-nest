/* ============================================================================
   MediaNest — Settings screen
   Registers route 'settings'.
   Builds HTML with single-quote concatenation only.
   ========================================================================== */

(() => {
	var MN = window.MN;
	var DATA = window.MN_DATA;

	/* ----- module-local state ------------------------------------------------ */
	var state = {
		// VPS Sync
		serverUrl: "https://vps.example.com:8000",
		apiKey: "mn_sec_9942a78f0d8e23",
		syncInterval: 6,
		syncState: "idle", // idle | syncing | success | error
		syncMessage: "",
		lastSyncAt: Date.now() - 3600000,
		deviceId: "mn-device-4f2a",
		logExpanded: false,

		// Downloads & Network
		downloadLocation: "/storage/emulated/0/Download/MediaNest",
		defaultResolution: "360p",
		maxConcurrentDownloads: 2,

		// Content & Display Preferences
		showShorts: false,
		collectionsViewMode: "GRID", // GRID | LIST
		autoMarkWatched: true,
		backgroundPlayback: true,

		// Data Management
		autoBackupInterval: 24,
		repairState: "idle", // idle | running | done
		cleanerState: "idle", // idle | scanning | done

		// Updates & About
		autoCheckInterval: 24,
		updateState: "idle", // idle | checking | available | downloading | ready
		updateProgress: 0,
	};

	var syncLog = [
		{
			type: "push",
			table: "history",
			summary: "Pushed 12 history rows",
			time: Date.now() - 3600000,
		},
		{
			type: "pull",
			table: "subscriptions",
			summary: "Pulled 6 subscriptions",
			time: Date.now() - 7200000,
		},
		{
			type: "apply",
			table: "settings",
			summary: "Applied server settings",
			time: Date.now() - 14400000,
		},
		{
			type: "pull",
			table: "playlists",
			summary: "Synced 4 remote playlists",
			time: Date.now() - 28800000,
		},
		{
			type: "error",
			table: "sync",
			summary: "Connection timed out (auto-retried)",
			time: Date.now() - 86400000,
		},
	];

	var backupLog = [
		{
			name: "backup_metadata_2026-08-12.zip",
			lastModified: Date.now() - 86400000,
			sizeBytes: 128000,
			isFull: false,
		},
		{
			name: "backup_full_2026-08-09.zip",
			lastModified: Date.now() - 86400000 * 3,
			sizeBytes: 2140000000,
			isFull: true,
		},
	];

	var exportChoice = "metadata";
	var timers = [];

	function icon(n, cls) {
		return MN.icon(n, cls);
	}

	function later(fn, ms) {
		timers.push(setTimeout(fn, ms));
	}

	/* ----- building blocks --------------------------------------------------- */

	function statLine(items) {
		var html =
			'<div class="mn-row mn-gap-2" style="flex-wrap:wrap;padding:8px 16px 4px">';
		items.forEach((it) => {
			html +=
				'<span class="mn-tag">' +
				icon(it.icon, "mn-icon--sm") +
				" " +
				MN.esc(it.label) +
				" " +
				MN.esc(String(it.value)) +
				"</span>";
		});
		html += "</div>";
		return html;
	}

	function groupHtml(title, stats, inner) {
		return (
			'<section class="mn-settings-group">' +
			'<h2 style="color:var(--mn-accent);font-size:17px;font-weight:600;margin:0 0 8px">' +
			MN.esc(title) +
			"</h2>" +
			'<div class="mn-card">' +
			(stats && stats.length ? statLine(stats) : "") +
			inner +
			"</div>" +
			"</section>"
		);
	}

	function noteBox(title, notes, iconName, type) {
		var cls = "mn-note-box";
		if (type === "warning") cls += " mn-note-box--warning";
		if (type === "success") cls += " mn-note-box--success";
		var html = '<div class="' + cls + '">';
		if (title) {
			html +=
				'<div class="mn-note-box__header">' +
				icon(iconName || "info", "mn-icon--sm") +
				"<span>" +
				MN.esc(title) +
				"</span>" +
				"</div>";
		}
		html += '<div class="mn-note-box__content">';
		if (Array.isArray(notes)) {
			notes.forEach((n) => {
				html += "<p>• " + n + "</p>";
			});
		} else {
			html += "<p>" + notes + "</p>";
		}
		html += "</div></div>";
		return html;
	}

	function navRow(o) {
		return (
			'<button class="mn-setting-row" data-act="' +
			o.act +
			'" data-arg="' +
			MN.esc(o.arg || "") +
			'">' +
			'<span class="mn-setting-row__icon">' +
			icon(o.icon) +
			"</span>" +
			'<span class="mn-setting-row__body" style="display:flex;flex-direction:column;min-width:0">' +
			'<span class="mn-setting-row__title">' +
			MN.esc(o.title) +
			"</span>" +
			(o.desc
				? '<span class="mn-setting-row__desc">' + MN.esc(o.desc) + "</span>"
				: "") +
			"</span>" +
			(o.value
				? '<span class="mn-setting-row__value">' + MN.esc(o.value) + "</span>"
				: "") +
			'<span class="mn-setting-row__chevron">' +
			icon("chevron-right") +
			"</span>" +
			"</button>"
		);
	}

	function switchRow(o) {
		return (
			'<div class="mn-setting-row" data-act="' +
			o.act +
			'" style="cursor:pointer">' +
			'<span class="mn-setting-row__icon">' +
			icon(o.icon) +
			"</span>" +
			'<span class="mn-setting-row__body" style="display:flex;flex-direction:column;min-width:0;padding-right:12px">' +
			'<span class="mn-setting-row__title">' +
			MN.esc(o.title) +
			"</span>" +
			(o.desc
				? '<span class="mn-setting-row__desc" style="margin-top:2px;line-height:1.4">' +
					MN.esc(o.desc) +
					"</span>"
				: "") +
			"</span>" +
			'<span class="mn-switch' +
			(o.checked ? " mn-switch--on" : "") +
			'" role="switch" aria-checked="' +
			(o.checked ? "true" : "false") +
			'"></span>' +
			"</div>"
		);
	}

	function infoRow(o) {
		return (
			'<div class="mn-setting-row" style="cursor:default">' +
			'<span class="mn-setting-row__icon">' +
			icon(o.icon) +
			"</span>" +
			'<span class="mn-setting-row__body" style="display:flex;flex-direction:column;min-width:0">' +
			'<span class="mn-setting-row__title">' +
			MN.esc(o.title) +
			"</span>" +
			(o.desc
				? '<span class="mn-setting-row__desc">' + MN.esc(o.desc) + "</span>"
				: "") +
			"</span>" +
			(o.value
				? '<span class="mn-setting-row__value" style="user-select:all">' +
					MN.esc(o.value) +
					"</span>"
				: "") +
			(o.actionIcon
				? '<button class="mn-icon-btn mn-icon-btn--sm" data-act="' +
					o.act +
					'" aria-label="' +
					MN.esc(o.actionLabel || "") +
					'">' +
					icon(o.actionIcon) +
					"</button>"
				: "") +
			"</div>"
		);
	}

	function divider() {
		return '<hr class="mn-divider" style="margin:6px 0">';
	}

	function fieldHtml(label, value, iconName, id, type, placeholder, tip) {
		return (
			'<div style="margin:10px 0;width:100%">' +
			'<label style="display:block;font-size:12px;font-weight:600;color:var(--mn-text-primary);margin-bottom:4px" for="' +
			id +
			'">' +
			MN.esc(label) +
			"</label>" +
			'<div class="mn-field" style="width:100%">' +
			(iconName ? icon(iconName, "mn-icon--sm") : "") +
			'<input id="' +
			id +
			'" type="' +
			(type || "text") +
			'" value="' +
			MN.esc(value) +
			'" placeholder="' +
			MN.esc(placeholder || "") +
			'" aria-label="' +
			MN.esc(label) +
			'"/>' +
			"</div>" +
			(tip
				? '<p class="mn-muted" style="font-size:11px;margin:3px 4px 0">' +
					MN.esc(tip) +
					"</p>"
				: "") +
			"</div>"
		);
	}

	/* ----- select via sheet -------------------------------------------------- */

	function openSelect(title, options, current, onPick) {
		var body = '<div class="mn-list">';
		options.forEach((o) => {
			var active = String(o.value) === String(current);
			body +=
				'<button class="mn-select__option' +
				(active ? " mn-select__option--active" : "") +
				'" data-value="' +
				MN.esc(String(o.value)) +
				'">' +
				'<span style="display:flex;flex-direction:column;text-align:left">' +
				'<span style="font-weight:' +
				(active ? "600" : "500") +
				'">' +
				MN.esc(o.label) +
				"</span>" +
				(o.desc
					? '<span class="mn-muted" style="font-size:11.5px;margin-top:2px">' +
						MN.esc(o.desc) +
						"</span>"
					: "") +
				"</span>" +
				(active ? icon("check", "mn-icon--sm") : "") +
				"</button>";
		});
		body += "</div>";
		MN.sheet({
			title: title,
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll(".mn-select__option").forEach((btn) => {
					btn.onclick = () => {
						MN.closeSheet();
						onPick(btn.getAttribute("data-value"));
					};
				});
			},
		});
	}

	/* ----- radio helper ------------------------------------------------------ */

	function radioOption(value, title, desc, checked) {
		return (
			'<button class="mn-dialog__option" data-radio="' +
			value +
			'" style="width:100%">' +
			'<span class="mn-radio' +
			(checked ? " mn-radio--checked" : "") +
			'"></span>' +
			'<span style="display:flex;flex-direction:column;text-align:left">' +
			'<span class="mn-dialog__option__title">' +
			MN.esc(title) +
			"</span>" +
			(desc
				? '<span class="mn-dialog__option__desc">' + MN.esc(desc) + "</span>"
				: "") +
			"</span>" +
			"</button>"
		);
	}

	function wireRadio(rootEl, onChange) {
		rootEl.querySelectorAll("[data-radio]").forEach((btn) => {
			btn.onclick = () => {
				rootEl.querySelectorAll(".mn-radio").forEach((r) => {
					r.classList.remove("mn-radio--checked");
				});
				btn.querySelector(".mn-radio").classList.add("mn-radio--checked");
				if (onChange) onChange(btn.getAttribute("data-radio"));
			};
		});
	}

	/* ----- section renderers ------------------------------------------------- */

	function syncSection() {
		var inner = '<div style="padding:14px 16px 8px">';

		inner += noteBox(
			"VPS Cloud Sync",
			"Synchronize watch history, favorites, custom folders, playlists, and subscription channels across your devices using your private self-hosted VPS server instance. Media files are stored locally and not transmitted over sync.",
			"cloud",
		);

		inner += fieldHtml(
			"VPS Server URL",
			state.serverUrl,
			"cloud",
			"inp-server",
			"url",
			"https://your-vps-ip:8000",
			"The HTTPS endpoint of your self-hosted MediaNest backend sync server.",
		);
		inner += fieldHtml(
			"API Key",
			state.apiKey,
			"settings",
			"inp-api",
			"password",
			"Secret API Key",
			"Cryptographic API token issued during your server instance deployment.",
		);

		inner +=
			'<div class="mn-row mn-gap-2" style="margin-top:8px">' +
			'<button class="mn-btn mn-btn--primary mn-btn--sm mn-fill" data-act="register">' +
			icon("device", "mn-icon--sm") +
			" Register Device</button>" +
			'<button class="mn-btn mn-btn--secondary mn-btn--sm mn-fill" data-act="sync">' +
			icon("cloud-up", "mn-icon--sm") +
			" Sync Now</button>" +
			"</div>";

		if (state.syncState === "syncing") {
			inner +=
				'<div class="mn-row mn-gap-2" style="margin-top:10px"><span class="mn-spinner"></span><span class="mn-muted" style="font-size:13px">' +
				(state.syncMessage || "Syncing with VPS...") +
				"</span></div>";
		} else if (state.syncState === "success") {
			inner +=
				'<div class="mn-row mn-gap-2" style="margin-top:10px"><span class="mn-success">' +
				icon("check-circle", "mn-icon--sm") +
				'</span><span class="mn-success" style="font-size:13px">' +
				MN.esc(state.syncMessage) +
				"</span></div>";
		} else if (state.syncState === "error") {
			inner +=
				'<div class="mn-row mn-gap-2" style="margin-top:10px"><span class="mn-error">' +
				icon("warning", "mn-icon--sm") +
				'</span><span class="mn-error" style="font-size:13px">' +
				MN.esc(state.syncMessage) +
				"</span></div>";
		}

		inner += "</div>";

		inner += divider();
		inner += navRow({
			act: "interval",
			icon: "history",
			title: "Auto-sync interval",
			desc: "How often background worker syncs changes",
			value:
				state.syncInterval === 0
					? "Manual only"
					: "Every " + state.syncInterval + "h",
		});

		if (state.lastSyncAt > 0) {
			inner += infoRow({
				icon: "cloud-down",
				title: "Last synced",
				desc: "Timestamp of last successful bidirectional exchange",
				value: MN.fmt.rel(state.lastSyncAt),
			});
		}
		if (state.deviceId) {
			inner += infoRow({
				icon: "device",
				title: "Device ID",
				desc: "Hardware identifier registered with the VPS",
				value: state.deviceId,
				actionIcon: "copy",
				actionLabel: "Copy Device ID",
				act: "copy-device-id",
			});
		}

		inner += divider();
		inner +=
			'<div class="mn-row mn-justify-between" style="padding:4px 16px">' +
			'<button class="mn-setting-row" data-act="toggle-log" style="padding:8px 0;flex:1">' +
			'<span class="mn-setting-row__icon">' +
			icon("list") +
			"</span>" +
			'<span class="mn-setting-row__body" style="display:flex;flex-direction:column;min-width:0">' +
			'<span class="mn-setting-row__title">Sync Activity Log</span>' +
			'<span class="mn-setting-row__desc">' +
			syncLog.length +
			" recorded transactions</span>" +
			"</span>" +
			'<span class="mn-setting-row__chevron">' +
			icon(state.logExpanded ? "chevron-up" : "chevron-down") +
			"</span>" +
			"</button>" +
			(state.logExpanded && syncLog.length
				? '<button class="mn-btn mn-btn--ghost mn-btn--xs" data-act="clear-log" style="margin-left:8px">Clear</button>'
				: "") +
			"</div>";

		if (state.logExpanded) {
			inner +=
				'<div class="mn-list mn-list--dense" style="padding:4px 16px 10px">';
			if (syncLog.length) {
				syncLog.forEach((e) => {
					var ic = "info";
					var cls = "";
					if (e.type === "push") {
						ic = "cloud-up";
						cls = "mn-accent";
					} else if (e.type === "pull") {
						ic = "cloud-down";
						cls = "mn-accent";
					} else if (e.type === "apply") {
						ic = "edit";
						cls = "mn-accent";
					} else if (e.type === "error") {
						ic = "warning";
						cls = "mn-error";
					}
					inner +=
						'<div class="mn-row mn-gap-2" style="padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.04)">' +
						'<span class="' +
						cls +
						'">' +
						icon(ic, "mn-icon--sm") +
						"</span>" +
						'<span style="font-size:12px;color:var(--mn-text-secondary)" class="mn-fill">' +
						MN.esc("[" + e.table + "] " + e.summary) +
						"</span>" +
						'<span style="font-size:11px;color:var(--mn-text-secondary);flex-shrink:0">' +
						MN.fmt.rel(e.time) +
						"</span>" +
						"</div>";
				});
			} else {
				inner +=
					'<p class="mn-muted" style="font-size:12px;margin:6px 0">No sync activity logged yet.</p>';
			}
			inner += "</div>";
		}

		return groupHtml(
			"VPS Sync & Cloud",
			[
				{ icon: "cloud", label: "Status", value: "Active" },
				{ icon: "history", label: "Interval", value: state.syncInterval + "h" },
				{ icon: "list", label: "Logs", value: syncLog.length },
			],
			inner,
		);
	}

	function downloadsSection() {
		var inner = '<div style="padding:12px 16px 4px">';

		inner += noteBox(
			"Downloads & Storage Rules",
			"Configure storage destination, stream resolution defaults, and background network concurrency limits.",
			"download",
		);
		inner += "</div>";

		inner += navRow({
			act: "location",
			icon: "folder",
			title: "Download Location",
			desc: state.downloadLocation,
			value: "Change",
		});

		inner += navRow({
			act: "default-res",
			icon: "video",
			title: "Default Download Resolution",
			desc: "Preferred quality for single-click & auto downloads",
			value: state.defaultResolution,
		});

		inner += navRow({
			act: "max-concurrent",
			icon: "sliders",
			title: "Max Concurrent Downloads",
			desc: "Simultaneous network streams",
			value: state.maxConcurrentDownloads + " parallel",
		});

		var total = DATA.downloads.length;
		var done = DATA.downloads.filter((d) => d.status === "COMPLETED").length;
		return groupHtml(
			"Downloads & Network",
			[
				{ icon: "download", label: "Total", value: total },
				{ icon: "check-circle", label: "Completed", value: done },
				{ icon: "video", label: "Quality", value: state.defaultResolution },
			],
			inner,
		);
	}

	function preferencesSection() {
		var inner = '<div style="padding:12px 16px 4px">';

		inner += noteBox(
			"Display & Playback Preferences",
			"Customize feed content filtering, default collection layouts, and video playback thresholds.",
			"sliders",
		);
		inner += "</div>";

		inner += switchRow({
			act: "toggle-shorts",
			icon: "youtube",
			title: "Show YouTube Shorts",
			desc:
				"Include YouTube Shorts in subscriptions and feed lists. When disabled, short-form portrait videos under 60s are filtered out.",
			checked: state.showShorts,
		});

		inner += divider();

		inner += navRow({
			act: "collections-view",
			icon: state.collectionsViewMode === "GRID" ? "grid" : "list",
			title: "Collections Default View Mode",
			desc:
				"Sets initial layout for History, Watched, Folders, Favorites, Playlists & Channels",
			value: state.collectionsViewMode === "GRID" ? "Grid" : "List",
		});

		inner += divider();

		inner += switchRow({
			act: "toggle-auto-watched",
			icon: "watched",
			title: "Auto-mark as Watched",
			desc:
				"Automatically mark videos as watched when remaining playback time is ≤ 1 minute or playback reaches 95%.",
			checked: state.autoMarkWatched,
		});

		inner += switchRow({
			act: "toggle-background-play",
			icon: "music",
			title: "Background Audio Playback",
			desc:
				"Continue playing audio smoothly in the background when navigating away or locking your screen.",
			checked: state.backgroundPlayback,
		});

		return groupHtml(
			"Preferences",
			[
				{
					icon: "youtube",
					label: "Shorts",
					value: state.showShorts ? "Show" : "Hide",
				},
				{
					icon: "grid",
					label: "View",
					value: state.collectionsViewMode === "GRID" ? "Grid" : "List",
				},
			],
			inner,
		);
	}

	function dataSection() {
		var missing = DATA.downloads.filter(
			(d) => d.status === "FAILED" || d.errorMessage === "file_missing",
		).length;

		var inner = '<div style="padding:6px 0">';

		inner += navRow({
			act: "stats",
			icon: "chart",
			title: "App Statistics",
			desc:
				"Detailed playback metrics, top channels, category distribution & storage footprint",
			value: "View",
		});

		inner += divider();

		inner += '<div style="padding:12px 16px 10px">';
		inner +=
			'<span class="mn-key" style="font-size:12px">Backup &amp; Restore</span>';
		inner +=
			'<p class="mn-muted" style="font-size:12px;margin:4px 0 10px">Export library database and downloaded media to a ZIP archive, or restore from one.</p>';

		inner += noteBox("Backup & Restore Explanations", [
			"<strong>Export details:</strong> Packages all database records (videos list, subscriptions, watch history & timestamps, custom folders, playlists, and preferences) into a portable ZIP archive. You can optionally include downloaded video & audio files for a full offline backup.",
			"<strong>Import details:</strong> Overwrites database with imported records and re-extracts video & audio files to their designated local storage paths.",
			"<strong>Download Missing Files:</strong> Appears when files are missing on disk. Clicking it will re-queue and re-download completed files that are absent. Do not run 'Repair Library' first, as it will clear database references to those files.",
		]);

		inner +=
			'<div class="mn-row mn-gap-2" style="margin-top:10px">' +
			'<button class="mn-btn mn-btn--primary mn-btn--sm mn-fill" data-act="export">' +
			icon("extract", "mn-icon--sm") +
			" Export Backup</button>" +
			'<button class="mn-btn mn-btn--secondary mn-btn--sm mn-fill" data-act="import">' +
			icon("file", "mn-icon--sm") +
			" Import Backup</button>" +
			"</div>";
		inner += "</div>";

		inner += divider();

		inner += navRow({
			act: "auto-backup",
			icon: "history",
			title: "Auto-backup interval",
			desc:
				"Automatically saves metadata-only backups in 'backup/' in download directory",
			value: autoBackupLabel(state.autoBackupInterval),
		});

		if (state.autoBackupInterval > 0) {
			inner +=
				'<div style="padding:0 16px 8px">' +
				'<div class="mn-row mn-gap-2" style="font-size:11.5px">' +
				'<span class="mn-success" style="font-weight:600">Status: Active</span>' +
				'<span class="mn-muted">•</span>' +
				'<span class="mn-muted">Next Backup: in 21h 30m</span>' +
				"</div></div>";
		}

		if (backupLog.length) {
			inner +=
				'<div style="padding:6px 16px 2px"><span class="mn-key" style="font-size:11px">Local Backup Log (Max 3 retained)</span></div>';
			backupLog.forEach((b) => {
				inner +=
					'<div class="mn-setting-row" style="padding:10px 16px">' +
					'<span class="mn-setting-row__icon">' +
					icon("file") +
					"</span>" +
					'<span class="mn-setting-row__body" style="display:flex;flex-direction:column;min-width:0">' +
					'<span class="mn-setting-row__title" style="font-size:12.5px">' +
					MN.esc(b.name) +
					"</span>" +
					'<span class="mn-setting-row__desc">' +
					MN.esc(
						(b.isFull ? "Full Backup" : "Metadata") +
							" · " +
							MN.fmt.bytes(b.sizeBytes) +
							" · " +
							MN.fmt.rel(b.lastModified),
					) +
					"</span>" +
					"</span>" +
					'<button class="mn-icon-btn mn-icon-btn--sm" data-act="restore" data-arg="' +
					MN.esc(b.name) +
					'" aria-label="Restore" title="Restore Backup">' +
					icon("history") +
					"</button>" +
					'<button class="mn-icon-btn mn-icon-btn--sm" data-act="delete-backup" data-arg="' +
					MN.esc(b.name) +
					'" aria-label="Delete" title="Delete Backup" style="color:var(--mn-destructive)">' +
					icon("trash") +
					"</button>" +
					"</div>";
			});
		}

		inner += divider();

		if (missing > 0) {
			inner +=
				'<div style="padding:12px 16px 10px">' +
				noteBox(
					"Missing Media Files Detected",
					"Found " +
						missing +
						" completed video record(s) with missing files on storage disk. Click below to re-queue them for background download. Tip: Do NOT run 'Repair Library' first, as it will clear database references to missing files!",
					"warning",
					"warning",
				) +
				'<button class="mn-btn mn-btn--secondary mn-btn--block mn-btn--sm" data-act="redownload" style="margin-top:6px">' +
				icon("download", "mn-icon--sm") +
				" Download Missing Files (" +
				missing +
				")</button>" +
				"</div>";
			inner += divider();
		}

		inner += '<div style="padding:12px 16px 10px">';
		inner +=
			'<span class="mn-key" style="font-size:12px">Library Maintenance</span>';
		inner +=
			'<p class="mn-muted" style="font-size:12px;margin:4px 0 10px">Fix broken storage paths and purge abandoned media files.</p>';

		inner += noteBox("Library Repair Details", [
			"Scans video/audio storage directories on disk.",
			"If a video is on disk but has an incorrect path in the database, it fixes the path.",
			"If a video in the database is missing on disk, it clears its offline status.",
			"Any files on disk not linked to any database video or download are cleaned up as orphans. It does NOT search for or add new videos to your library.",
		]);

		inner +=
			'<button class="mn-btn mn-btn--secondary mn-btn--block mn-btn--sm" data-act="repair" ' +
			(state.repairState === "running" ? "disabled" : "") +
			">" +
			(state.repairState === "running"
				? '<span class="mn-spinner"></span> Repairing...'
				: icon("repair", "mn-icon--sm") + " Repair Library") +
			"</button>";

		if (state.repairState === "running") {
			inner +=
				'<div style="margin-top:10px"><div class="mn-bar"><span style="width:65%"></span></div>' +
				'<p class="mn-muted" style="font-size:12px;margin-top:6px">Scanning storage directories and validating database references...</p></div>';
		}
		inner += "</div>";

		inner += divider();

		inner += '<div style="padding:12px 16px 14px">';
		inner +=
			'<span class="mn-key" style="font-size:12px">Broken Media Cleaner</span>';
		inner +=
			'<p class="mn-muted" style="font-size:12px;margin:4px 0 10px">Scan and clean up temporary .tmp files, interrupted .part downloads, and orphaned fragments on disk taking up storage.</p>';

		inner +=
			'<button class="mn-btn mn-btn--secondary mn-btn--block mn-btn--sm" data-act="cleaner" ' +
			(state.cleanerState === "scanning" ? "disabled" : "") +
			">" +
			(state.cleanerState === "scanning"
				? '<span class="mn-spinner"></span> Scanning storage...'
				: icon("trash", "mn-icon--sm") + " Scan for Broken Files") +
			"</button>";
		inner += "</div>";

		inner += "</div>";

		return groupHtml(
			"Data Management & Storage",
			[
				{ icon: "file", label: "Backups", value: backupLog.length },
				{ icon: "download", label: "Missing", value: missing },
				{
					icon: "repair",
					label: "Repair",
					value: state.repairState === "done" ? "Repaired" : "Ready",
				},
			],
			inner,
		);
	}

	function aboutSection() {
		var inner = '<div style="padding:6px 0">';

		inner += infoRow({
			icon: "info",
			title: "MediaNest App",
			desc:
				"A premium offline-first media manager and subscription player designed to organize, save, and stream your favorite content seamlessly.",
		});
		inner += infoRow({
			icon: "device",
			title: "Version",
			desc: "Channel: Stable release build",
			value: "v1.0.0 (Build 2408)",
		});
		inner += infoRow({
			icon: "edit",
			title: "Author & Developer",
			desc: "Open source community release",
			value: "Kushal",
		});

		inner += divider();

		inner += '<div style="padding:12px 16px 6px">';
		inner +=
			'<span class="mn-key" style="font-size:12px">Application Updates</span>';
		inner +=
			'<p class="mn-muted" style="font-size:12px;margin:4px 0 10px">Installed version: v1.0.0. Updates are checked automatically from GitHub releases, and you can check manually at any time.</p>';
		inner += "</div>";

		inner += navRow({
			act: "auto-check",
			icon: "refresh",
			title: "Auto-check for updates",
			desc: "Frequency of background update checks",
			value: autoCheckLabel(state.autoCheckInterval),
		});

		inner += '<div style="padding:8px 16px 14px">';
		inner +=
			'<button class="mn-btn mn-btn--primary mn-btn--block" data-act="check-updates" ' +
			(state.updateState === "checking" || state.updateState === "downloading"
				? "disabled"
				: "") +
			">" +
			(state.updateState === "checking"
				? '<span class="mn-spinner"></span> Checking GitHub releases...'
				: icon("refresh", "mn-icon--sm") + " Check for Updates") +
			"</button>";
		inner += "</div>";

		if (state.updateState === "checking") {
			inner +=
				'<div style="padding:0 16px 16px" class="mn-row mn-gap-2"><span class="mn-spinner"></span><span class="mn-muted" style="font-size:13px">Checking GitHub repository for latest release...</span></div>';
		} else if (state.updateState === "available") {
			inner += '<div style="padding:0 16px 16px">';
			inner +=
				'<div class="mn-card mn-card--raised mn-card--pad" style="margin-bottom:10px">' +
				'<p style="margin:0 0 4px;font-weight:600;color:var(--mn-accent)">New Version Available: v2.1.0</p>' +
				'<p class="mn-muted" style="font-size:12px;margin:0 0 8px;line-height:1.4">Faster extraction engine, redesigned glassmorphism UI, playlist autoplay, background download queue reliability, and storage migration fixes.</p>' +
				'<div class="mn-row mn-gap-2">' +
				'<button class="mn-btn mn-btn--primary mn-btn--sm mn-fill" data-act="download-update">' +
				icon("download", "mn-icon--sm") +
				" Download &amp; Install</button>" +
				'<button class="mn-btn mn-btn--ghost mn-btn--sm" data-act="dismiss-update">Dismiss</button>' +
				"</div>" +
				"</div>";
			inner += "</div>";
		} else if (state.updateState === "downloading") {
			inner +=
				'<div style="padding:0 16px 16px">' +
				'<p class="mn-muted" style="font-size:13px;margin:0 0 6px">Downloading APK update: ' +
				state.updateProgress +
				"%</p>" +
				'<div class="mn-bar"><span style="width:' +
				state.updateProgress +
				'%"></span></div>' +
				"</div>";
		} else if (state.updateState === "ready") {
			inner +=
				'<div style="padding:0 16px 16px">' +
				'<div class="mn-card mn-card--raised mn-card--pad" style="margin-bottom:10px;background:rgba(34,197,94,0.1);border-color:rgba(34,197,94,0.3)">' +
				'<p style="margin:0 0 4px;font-weight:600;color:var(--mn-success)">Update Downloaded: v2.1.0</p>' +
				'<p class="mn-muted" style="font-size:12px;margin:0 0 10px">The APK has been verified and is ready to install. The application will restart upon installation.</p>' +
				'<button class="mn-btn mn-btn--primary mn-btn--block" data-act="install">' +
				icon("check-circle", "mn-icon--sm") +
				" Install Update Now</button>" +
				"</div>" +
				"</div>";
		}

		inner += divider();
		inner += navRow({
			act: "notifications",
			icon: "bell",
			title: "Notifications Hub",
			desc: "Download events, subscription releases & sync notices (365-day log)",
			value: String(DATA.notifications.length),
		});

		inner += "</div>";
		return groupHtml(
			"About & Updates",
			[
				{ icon: "device", label: "Version", value: "v1.0.0" },
				{
					icon: "bell",
					label: "Notifs",
					value: DATA.notifications.length,
				},
			],
			inner,
		);
	}

	function autoBackupLabel(v) {
		if (v === 0) return "Disabled (Off)";
		if (v === 168) return "Every 7 days";
		return "Every " + v + "h";
	}
	function autoCheckLabel(v) {
		if (v === 0) return "Off (Manual only)";
		if (v === 168) return "Every 7 days";
		return "Every 24 hours (daily)";
	}

	/* ----- full render ------------------------------------------------------- */

	function html() {
		return (
			'<div style="padding-bottom:16px">' +
			syncSection() +
			downloadsSection() +
			preferencesSection() +
			dataSection() +
			aboutSection() +
			"</div>"
		);
	}

	function render(el) {
		MN.render(el, html());
		wire(el);
	}

	/* ----- event wiring ------------------------------------------------------ */

	function wire(el) {
		var server = MN.qs("#inp-server", el);
		var api = MN.qs("#inp-api", el);
		if (server)
			server.oninput = () => {
				state.serverUrl = server.value;
			};
		if (api)
			api.oninput = () => {
				state.apiKey = api.value;
			};

		el.querySelectorAll("[data-act]").forEach((btn) => {
			btn.onclick = () => {
				var act = btn.getAttribute("data-act");
				var arg = btn.getAttribute("data-arg") || "";
				handleAction(el, act, arg);
			};
		});
	}

	function handleAction(el, act, arg) {
		if (act === "register") {
			state.syncState = "syncing";
			state.syncMessage = "Registering device with VPS...";
			render(el);
			later(() => {
				state.deviceId = "mn-device-4f2a";
				state.syncState = "success";
				state.syncMessage = "Device successfully registered with VPS";
				state.lastSyncAt = Date.now();
				syncLog.unshift({
					type: "apply",
					table: "device",
					summary: "Device registered as mn-device-4f2a",
					time: Date.now(),
				});
				MN.toast("Device registered successfully", "success");
				render(el);
			}, 1500);
			return;
		}

		if (act === "sync") {
			state.syncState = "syncing";
			state.syncMessage = "Syncing watch history & subscriptions...";
			render(el);
			later(() => {
				state.syncState = "success";
				state.syncMessage =
					"Sync complete • 12 history rows pushed, 6 subscriptions pulled";
				state.lastSyncAt = Date.now();
				syncLog.unshift({
					type: "pull",
					table: "history",
					summary: "Bidirectional sync completed",
					time: Date.now(),
				});
				MN.toast("Sync complete", "success");
				MN.notify({
					type: "success",
					title: "VPS Sync Completed",
					desc: "History, subscriptions and folders synchronized across devices.",
					channel: "VPS Sync",
				});
				render(el);
			}, 1800);
			return;
		}

		if (act === "copy-device-id") {
			if (navigator.clipboard && navigator.clipboard.writeText) {
				navigator.clipboard.writeText(state.deviceId);
			}
			MN.toast("Device ID copied to clipboard: " + state.deviceId, "info");
			return;
		}

		if (act === "interval") {
			openSelect(
				"Auto-sync interval",
				[
					{
						value: 1,
						label: "Every 1 hour",
						desc: "Frequent sync (ideal for multi-device active watching)",
					},
					{
						value: 2,
						label: "Every 2 hours",
						desc: "Regular updates throughout the day",
					},
					{
						value: 6,
						label: "Every 6 hours (Recommended)",
						desc: "Optimal balance of battery and freshness",
					},
					{
						value: 12,
						label: "Every 12 hours",
						desc: "Twice daily synchronization",
					},
					{
						value: 24,
						label: "Every 24 hours",
						desc: "Daily background sync cycle",
					},
					{
						value: 0,
						label: "Manual only",
						desc: "Disable background worker sync",
					},
				],
				state.syncInterval,
				(v) => {
					state.syncInterval = parseInt(v, 10);
					render(el);
					MN.toast(
						state.syncInterval === 0
							? "Auto-sync disabled (manual only)"
							: "Auto-sync set to every " + v + "h",
						"info",
					);
				},
			);
			return;
		}

		if (act === "toggle-log") {
			state.logExpanded = !state.logExpanded;
			render(el);
			return;
		}

		if (act === "clear-log") {
			syncLog = [];
			render(el);
			MN.toast("Sync activity log cleared", "info");
			return;
		}

		if (act === "location") {
			MN.sheet({
				title: "Download Location",
				body:
					'<div style="padding:4px 0">' +
					noteBox(
						"Folder Migration Notice",
						"Media files and offline thumbnails are stored in this directory. Changing the location automatically migrates your existing downloaded files to the new destination. Ensure the path is writable by the app.",
						"folder",
					) +
					'<div class="mn-field" style="margin:12px 0"><span>' +
					icon("folder", "mn-icon--sm") +
					"</span>" +
					'<input id="inp-location" type="text" value="' +
					MN.esc(state.downloadLocation) +
					'"/></div>' +
					'<button class="mn-btn mn-btn--primary mn-btn--block" id="btn-apply-location" style="margin-top:14px">' +
					icon("check", "mn-icon--sm") +
					" Apply &amp; Migrate Files</button>" +
					"</div>",
				onOpen: (bodyEl) => {
					var inp = bodyEl.querySelector("#inp-location");
					bodyEl.querySelector("#btn-apply-location").onclick = () => {
						state.downloadLocation = inp.value.trim() || state.downloadLocation;
						MN.closeSheet();
						render(el);
						MN.toast(
							"Download location updated to " + state.downloadLocation,
							"success",
						);
					};
				},
			});
			return;
		}

		if (act === "default-res") {
			openSelect(
				"Default Download Resolution",
				[
					{
						value: "1080p",
						label: "1080p (Full HD)",
						desc: "Crisp 1080p video stream (larger file sizes)",
					},
					{
						value: "720p",
						label: "720p (HD)",
						desc: "High definition (balanced quality and storage)",
					},
					{
						value: "480p",
						label: "480p (SD)",
						desc: "Standard definition (good for mobile screens)",
					},
					{
						value: "360p",
						label: "360p (Low data - Recommended)",
						desc: "Compact file size, fast downloads, minimal storage",
					},
					{
						value: "Audio",
						label: "Audio Only (Opus/M4A)",
						desc: "Extracts audio track only (perfect for music & podcasts)",
					},
				],
				state.defaultResolution,
				(v) => {
					state.defaultResolution = v;
					MN.store.set({ defaultResolution: v });
					render(el);
					MN.toast("Default resolution set to " + v, "info");
				},
			);
			return;
		}

		if (act === "max-concurrent") {
			openSelect(
				"Max Concurrent Downloads",
				[
					{
						value: 1,
						label: "1 download at a time",
						desc: "Sequential downloading (best for slow connections)",
					},
					{
						value: 2,
						label: "2 parallel downloads (Default)",
						desc: "Recommended balance of network speed and CPU",
					},
					{
						value: 3,
						label: "3 parallel downloads",
						desc: "Faster queue completion on high-speed Wi-Fi",
					},
					{
						value: 4,
						label: "4 parallel downloads",
						desc: "Heavy concurrent downloading",
					},
					{
						value: 5,
						label: "5 parallel downloads",
						desc: "Maximum parallel network bandwidth throughput",
					},
				],
				state.maxConcurrentDownloads,
				(v) => {
					state.maxConcurrentDownloads = parseInt(v, 10);
					MN.store.set({ maxConcurrent: state.maxConcurrentDownloads });
					render(el);
					MN.toast(
						"Max concurrent downloads set to " + state.maxConcurrentDownloads,
						"info",
					);
				},
			);
			return;
		}

		if (act === "toggle-shorts") {
			state.showShorts = !state.showShorts;
			MN.store.set({ showShorts: state.showShorts });
			render(el);
			MN.toast(
				"Show Shorts in feeds: " + (state.showShorts ? "Enabled" : "Disabled"),
				"info",
			);
			return;
		}

		if (act === "collections-view") {
			openSelect(
				"Collections Default View Mode",
				[
					{
						value: "GRID",
						label: "Grid View (Recommended)",
						desc: "Visual thumbnail cards in a 2-column responsive layout",
					},
					{
						value: "LIST",
						label: "List View",
						desc: "Compact horizontal row layout with detailed metadata",
					},
				],
				state.collectionsViewMode,
				(v) => {
					state.collectionsViewMode = v;
					MN.store.set({ collectionsViewMode: v });
					render(el);
					MN.toast(
						"Collections default view: " + (v === "GRID" ? "Grid View" : "List View"),
						"info",
					);
				},
			);
			return;
		}

		if (act === "toggle-auto-watched") {
			state.autoMarkWatched = !state.autoMarkWatched;
			render(el);
			MN.toast(
				"Auto-mark as watched: " + (state.autoMarkWatched ? "Enabled" : "Disabled"),
				"info",
			);
			return;
		}

		if (act === "toggle-background-play") {
			state.backgroundPlayback = !state.backgroundPlayback;
			render(el);
			MN.toast(
				"Background audio playback: " +
					(state.backgroundPlayback ? "Enabled" : "Disabled"),
				"info",
			);
			return;
		}

		if (act === "stats") {
			MN.router.navigate("statistics");
			return;
		}

		if (act === "export") {
			exportChoice = "metadata";
			var body =
				radioOption(
					"metadata",
					"Metadata Only (Recommended)",
					"Exports database records, subscriptions, watch history, custom folders, playlists, and settings into a compact ZIP archive (< 1 MB).",
					true,
				) +
				radioOption(
					"full",
					"Full Library Archive",
					"Includes complete database plus all physical downloaded video and audio files into a full standalone archive (~2.1 GB).",
					false,
				);
			MN.dialog({
				title: "Export Library Backup",
				body: body,
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Export ZIP",
						cls: "mn-btn--primary",
						onClick: () => {
							doExport(el, exportChoice);
						},
					},
				],
			});
			wireRadio(MN.qs("#dialog"), (v) => {
				exportChoice = v;
			});
			return;
		}

		if (act === "import") {
			MN.dialog({
				title: "Import Library Backup",
				body:
					"<p style='line-height:1.4;margin:0 0 10px'>Select a MediaNest ZIP backup file to restore. This will restore database records and re-index media files.</p>" +
					noteBox(
						"Overwrite Notice",
						"Restoring a backup overwrites current database records with the imported snapshot.",
						"warning",
						"warning",
					),
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Choose File",
						cls: "mn-btn--primary",
						onClick: () => {
							MN.toast("Selecting backup file...", "info");
							later(() => {
								MN.toast("Library backup restored successfully", "success");
							}, 1200);
						},
					},
				],
			});
			return;
		}

		if (act === "auto-backup") {
			openSelect(
				"Auto-backup interval",
				[
					{
						value: 0,
						label: "Disabled (Off)",
						desc: "No automatic backups created",
					},
					{
						value: 6,
						label: "Every 6 hours",
						desc: "Frequent snapshots for active creators",
					},
					{
						value: 12,
						label: "Every 12 hours",
						desc: "Twice daily metadata backups",
					},
					{
						value: 24,
						label: "Every 24 hours (Daily)",
						desc: "Recommended daily backup cycle",
					},
					{
						value: 168,
						label: "Every 7 days",
						desc: "Weekly archival snapshot",
					},
				],
				state.autoBackupInterval,
				(v) => {
					state.autoBackupInterval = parseInt(v, 10);
					render(el);
					MN.toast(
						"Auto-backup " + autoBackupLabel(state.autoBackupInterval),
						"info",
					);
				},
			);
			return;
		}

		if (act === "restore") {
			MN.dialog({
				title: "Restore Local Backup",
				body:
					"<p style='line-height:1.4;margin:0 0 10px'>Restore library state from <strong>" +
					MN.esc(arg) +
					"</strong>?</p>" +
					noteBox(
						"Warning",
						"This will replace your current library database records with the snapshot stored in this backup archive.",
						"warning",
						"warning",
					),
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Restore Backup",
						cls: "mn-btn--primary",
						onClick: () => {
							MN.toast("Restoring backup archive...", "info");
							later(() => {
								MN.toast("Library restored from " + arg, "success");
							}, 1000);
						},
					},
				],
			});
			return;
		}

		if (act === "delete-backup") {
			MN.dialog({
				title: "Delete Local Backup",
				body:
					"<p style='line-height:1.4;margin:0'>Are you sure you want to permanently delete <strong>" +
					MN.esc(arg) +
					"</strong>? This file will be removed from storage.</p>",
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Delete File",
						cls: "mn-btn--danger-solid",
						onClick: () => {
							backupLog = backupLog.filter((b) => b.name !== arg);
							render(el);
							MN.toast("Backup archive deleted", "info");
						},
					},
				],
			});
			return;
		}

		if (act === "redownload") {
			MN.dialog({
				title: "Download Missing Files",
				body:
					"<p style='line-height:1.4;margin:0 0 10px'>Re-queue all completed video records whose media files are missing on storage?</p>" +
					noteBox(
						"Important Tip",
						"Running 'Repair Library' before re-downloading will clear database references to missing files, preventing automated recovery.",
						"info",
					),
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Queue Downloads",
						cls: "mn-btn--primary",
						onClick: () => {
							MN.toast(
								"Missing media files queued for background download",
								"success",
							);
						},
					},
				],
			});
			return;
		}

		if (act === "repair") {
			state.repairState = "running";
			render(el);
			later(() => {
				state.repairState = "done";
				render(el);
				MN.dialog({
					title: "Library Repair Completed",
					body:
						'<p class="mn-success" style="margin:0 0 8px;font-weight:600">Storage Scan &amp; Path Repair Complete</p>' +
						'<div class="mn-list mn-list--dense" style="line-height:1.5;font-size:12.5px">' +
						"<p style='margin:0'>• Corrected 2 mismatched absolute file paths</p>" +
						"<p style='margin:0'>• Cleared 1 unrecoverable missing offline flag</p>" +
						"<p style='margin:0'>• Re-indexed database file references</p>" +
						"</div>",
					actions: [
						{ label: "Close", cls: "mn-btn--ghost", onClick: () => {} },
						{
							label: "Show Details",
							cls: "mn-btn--primary",
							onClick: showRepairDetails,
						},
					],
				});
			}, 1800);
			return;
		}

		if (act === "cleaner") {
			state.cleanerState = "scanning";
			render(el);
			later(() => {
				state.cleanerState = "done";
				render(el);
				showCleanerResults(el);
			}, 1500);
			return;
		}

		if (act === "auto-check") {
			openSelect(
				"Auto-check for updates",
				[
					{
						value: 0,
						label: "Off (Manual only)",
						desc: "Only check when tapping Check for Updates button",
					},
					{
						value: 24,
						label: "Every 24 hours (Daily)",
						desc: "Checks GitHub releases once a day in the background",
					},
					{
						value: 168,
						label: "Every 7 days",
						desc: "Weekly background update check",
					},
				],
				state.autoCheckInterval,
				(v) => {
					state.autoCheckInterval = parseInt(v, 10);
					render(el);
					MN.toast("Auto-check " + autoCheckLabel(state.autoCheckInterval), "info");
				},
			);
			return;
		}

		if (act === "check-updates") {
			state.updateState = "checking";
			render(el);
			later(() => {
				state.updateState = "available";
				render(el);
			}, 1800);
			return;
		}

		if (act === "download-update") {
			state.updateState = "downloading";
			state.updateProgress = 0;
			render(el);
			var tick = setInterval(() => {
				state.updateProgress += 10;
				if (state.updateProgress >= 100) {
					clearInterval(tick);
					state.updateState = "ready";
				}
				render(el);
			}, 300);
			return;
		}

		if (act === "dismiss-update") {
			state.updateState = "idle";
			render(el);
			return;
		}

		if (act === "install") {
			MN.dialog({
				title: "Install MediaNest Update",
				body:
					"<p style='line-height:1.4'>Installing version <strong>v2.1.0</strong>... The application will restart upon completion.</p>",
				actions: [
					{
						label: "Restart &amp; Install",
						cls: "mn-btn--primary",
						onClick: () => {
							state.updateState = "idle";
							render(el);
							MN.toast("Update v2.1.0 installed successfully", "success");
						},
					},
				],
			});
			return;
		}

		if (act === "notifications") {
			MN.router.navigate("notifications");
			return;
		}
	}

	function doExport(el, choice) {
		if (choice === "full") {
			MN.toast("Exporting full library archive (~2.1 GB)...", "info");
			later(() => {
				var name = "backup_full_" + new Date().toISOString().slice(0, 10) + ".zip";
				backupLog.unshift({
					name: name,
					lastModified: Date.now(),
					sizeBytes: 2150000000,
					isFull: true,
				});
				MN.toast("Full backup exported to " + name, "success");
				render(el);
			}, 1500);
		} else {
			var metaName =
				"backup_metadata_" + new Date().toISOString().slice(0, 10) + ".zip";
			backupLog.unshift({
				name: metaName,
				lastModified: Date.now(),
				sizeBytes: 134000,
				isFull: false,
			});
			MN.toast("Metadata backup exported to " + metaName, "success");
			render(el);
		}
	}

	function showRepairDetails() {
		MN.dialog({
			title: "Library Repair Audit Log",
			body:
				'<div class="mn-list mn-list--dense" style="font-size:12.5px">' +
				'<p style="margin:0 0 4px;font-weight:600;color:var(--mn-accent)">Fixed Storage Paths</p>' +
				'<p class="mn-muted" style="margin:0">• vid_1001.mp4 → updated relative path to absolute location</p>' +
				'<p class="mn-muted" style="margin:0">• vid_1003.m4a → updated relative path to absolute location</p>' +
				'<p style="margin:10px 0 4px;font-weight:600;color:var(--mn-accent)">Cleared Offline Flags</p>' +
				'<p class="mn-muted" style="margin:0">• vid_1005 (File missing on disk — offline flag cleared)</p>' +
				'<p style="margin:10px 0 4px;font-weight:600;color:var(--mn-accent)">Database Verification</p>' +
				'<p class="mn-muted" style="margin:0">• Verified 14 watch history sessions and 6 channel subscriptions</p>' +
				"</div>",
			actions: [{ label: "Done", cls: "mn-btn--primary", onClick: () => {} }],
		});
	}

	function showCleanerResults(el) {
		var files = [
			{ name: "vid_1007.tmp.mp4", sizeBytes: 84000000, isAudio: false },
			{ name: "vid_1009.part.m4a", sizeBytes: 1200000, isAudio: true },
		];
		var total = files.reduce((a, b) => a + b.sizeBytes, 0);
		var body =
			"<p style='margin:0 0 8px'>Found <strong>" +
			files.length +
			" broken files</strong> taking up <strong>" +
			MN.fmt.bytes(total) +
			"</strong> of storage.</p>" +
			'<div class="mn-list mn-list--dense" style="max-height:220px;overflow-y:auto">';
		files.forEach((f) => {
			body +=
				'<div class="mn-row mn-gap-2" style="padding:8px 0;border-bottom:1px solid rgba(255,255,255,0.04)">' +
				'<span class="mn-muted">' +
				icon(f.isAudio ? "music" : "video", "mn-icon--sm") +
				"</span>" +
				'<span class="mn-fill" style="font-size:12.5px;min-width:0">' +
				'<span style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' +
				MN.esc(f.name) +
				"</span>" +
				'<span class="mn-muted" style="font-size:11px">' +
				(f.isAudio ? "Audio fragment" : "Video temp file") +
				" · " +
				MN.fmt.bytes(f.sizeBytes) +
				"</span>" +
				"</span>" +
				'<button class="mn-icon-btn mn-icon-btn--sm" data-clean-file="' +
				MN.esc(f.name) +
				'" style="color:var(--mn-destructive)" aria-label="Delete File">' +
				icon("trash") +
				"</button>" +
				"</div>";
		});
		body += "</div>";

		MN.dialog({
			title: "Broken Files Found",
			body: body,
			actions: [
				{ label: "Keep Files", cls: "mn-btn--ghost", onClick: () => {} },
				{
					label: "Delete All (" + MN.fmt.bytes(total) + ")",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						MN.toast(
							"Cleaned 2 broken media files (" + MN.fmt.bytes(total) + " freed)",
							"success",
						);
					},
				},
			],
		});
	}

	/* ----- register ---------------------------------------------------------- */

	MN.router.register("settings", {
		title: "Settings",
		back: false,
		mount: (el) => {
			render(el);
			return {
				unmount: () => {
					timers.forEach((t) => {
						clearTimeout(t);
					});
					timers = [];
				},
			};
		},
	});
})();
