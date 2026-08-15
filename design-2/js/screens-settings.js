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
		serverUrl: "https://vps.example.com:8000",
		apiKey: "",
		syncInterval: 6,
		syncState: "idle", // idle | syncing | success | error
		syncMessage: "",
		lastSyncAt: 0,
		deviceId: "",
		logExpanded: false,
		downloadLocation: "/storage/emulated/0/Download/MediaNest",
		defaultResolution: "360p",
		autoBackupInterval: 24,
		autoCheckInterval: 24,
		updateState: "idle", // idle | checking | available | downloading | ready
		updateProgress: 0,
		repairState: "idle", // idle | running | done
		cleanerState: "idle", // idle | scanning | done
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
			type: "error",
			table: "settings",
			summary: "Connection timed out",
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
			'<div class="mn-row mn-gap-2" style="flex-wrap:wrap;padding:6px 16px 4px">';
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
				? '<span class="mn-setting-row__value">' + MN.esc(o.value) + "</span>"
				: "") +
			"</div>"
		);
	}

	function divider() {
		return '<hr class="mn-divider" style="margin:6px 0">';
	}

	function fieldHtml(label, value, iconName, id, type, placeholder) {
		return (
			'<div class="mn-field" style="margin:8px 0">' +
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
				"<span>" +
				MN.esc(o.label) +
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
		var inner = '<div style="padding:14px 16px">';

		inner += fieldHtml(
			"VPS Server URL",
			state.serverUrl,
			"cloud",
			"inp-server",
			"url",
			"https://your-vps-ip:8000",
		);
		inner += fieldHtml(
			"API Key",
			state.apiKey,
			"settings",
			"inp-api",
			"password",
			"API key",
		);

		inner +=
			'<div class="mn-row mn-gap-2" style="margin-top:4px">' +
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

		inner += divider();
		inner += navRow({
			act: "interval",
			icon: "history",
			title: "Auto-sync interval",
			desc: "How often to sync automatically",
			value: "Every " + state.syncInterval + "h",
		});

		if (state.lastSyncAt > 0) {
			inner += infoRow({
				icon: "cloud-down",
				title: "Last synced",
				value: MN.fmt.rel(state.lastSyncAt),
			});
		}
		if (state.deviceId) {
			inner += infoRow({
				icon: "device",
				title: "Device ID",
				value: state.deviceId,
			});
		}

		inner += divider();
		inner +=
			'<button class="mn-setting-row" data-act="toggle-log">' +
			'<span class="mn-setting-row__icon">' +
			icon("list") +
			"</span>" +
			'<span class="mn-setting-row__body" style="display:flex;flex-direction:column;min-width:0">' +
			'<span class="mn-setting-row__title">Sync Log</span>' +
			'<span class="mn-setting-row__desc">' +
			syncLog.length +
			" entries</span>" +
			"</span>" +
			'<span class="mn-setting-row__chevron">' +
			icon(state.logExpanded ? "chevron-up" : "chevron-down") +
			"</span>" +
			"</button>";

		if (state.logExpanded) {
			inner += '<div class="mn-list mn-list--dense" style="padding:4px 0 10px">';
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
					'<div class="mn-row mn-gap-2" style="padding:6px 4px">' +
					'<span class="' +
					cls +
					'">' +
					icon(ic, "mn-icon--sm") +
					"</span>" +
					'<span style="font-size:12px;color:var(--mn-text-secondary)" class="mn-fill">' +
					MN.esc("[" + e.table + "] " + e.summary) +
					"</span>" +
					'<span style="font-size:11px;color:var(--mn-text-secondary)">' +
					MN.fmt.rel(e.time) +
					"</span>" +
					"</div>";
			});
			inner += "</div>";
		}

		inner += "</div>";
		return groupHtml(
			"VPS Sync",
			[
				{ icon: "cloud", label: "Logs", value: syncLog.length },
				{ icon: "history", label: "Interval", value: state.syncInterval + "h" },
			],
			inner,
		);
	}

	function downloadsSection() {
		var inner = '<div style="padding:6px 0">';
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
			desc: "Preferred resolution for new downloads",
			value: state.defaultResolution,
		});
		inner += "</div>";
		var total = DATA.downloads.length;
		var done = DATA.downloads.filter((d) => d.status === "COMPLETED").length;
		return groupHtml(
			"Downloads",
			[
				{ icon: "download", label: "Total", value: total },
				{ icon: "check-circle", label: "Completed", value: done },
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
			desc: "Usage data and watch history metrics",
		});

		inner += divider();

		inner += '<div style="padding:14px 16px">';
		inner += '<span class="mn-key">Backup &amp; Restore</span>';
		inner +=
			'<p class="mn-muted" style="font-size:12px;margin:4px 0 10px">Export library data and media to a ZIP, or restore from one.</p>';
		inner +=
			'<div class="mn-row mn-gap-2">' +
			'<button class="mn-btn mn-btn--primary mn-btn--sm mn-fill" data-act="export">' +
			icon("extract", "mn-icon--sm") +
			" Export Backup</button>" +
			'<button class="mn-btn mn-btn--secondary mn-btn--sm mn-fill" data-act="import">' +
			icon("file", "mn-icon--sm") +
			" Import Backup</button>" +
			"</div>";
		inner += "</div>";

		inner += navRow({
			act: "auto-backup",
			icon: "history",
			title: "Auto-backup interval",
			desc: "Metadata-only backups in download folder",
			value: autoBackupLabel(state.autoBackupInterval),
		});

		backupLog.forEach((b) => {
			inner +=
				'<div class="mn-setting-row">' +
				'<span class="mn-setting-row__icon">' +
				icon("file") +
				"</span>" +
				'<span class="mn-setting-row__body" style="display:flex;flex-direction:column;min-width:0">' +
				'<span class="mn-setting-row__title" style="font-size:12.5px">' +
				MN.esc(b.name) +
				"</span>" +
				'<span class="mn-setting-row__desc">' +
				MN.esc(
					(b.isFull ? "Full" : "Metadata") + " · " + MN.fmt.bytes(b.sizeBytes),
				) +
				"</span>" +
				"</span>" +
				'<button class="mn-icon-btn mn-icon-btn--sm" data-act="restore" data-arg="' +
				MN.esc(b.name) +
				'" aria-label="Restore">' +
				icon("history") +
				"</button>" +
				'<button class="mn-icon-btn mn-icon-btn--sm" data-act="delete-backup" data-arg="' +
				MN.esc(b.name) +
				'" aria-label="Delete" style="color:var(--mn-destructive)">' +
				icon("trash") +
				"</button>" +
				"</div>";
		});

		inner += divider();

		if (missing > 0) {
			inner += navRow({
				act: "redownload",
				icon: "download",
				title: "Download Missing Files (" + missing + ")",
				desc: "Re-queue completed files absent on disk",
				value: "Fix",
			});
		}

		inner += navRow({
			act: "repair",
			icon: "repair",
			title: "Library Repair",
			desc: "Scan media files and fix missing paths",
			value: state.repairState === "running" ? "Running..." : "Run",
		});
		if (state.repairState === "running") {
			inner +=
				'<div style="padding:0 16px 14px"><div class="mn-bar"><span style="width:60%"></span></div>' +
				'<p class="mn-muted" style="font-size:12px;margin-top:6px">Scanning storage directories...</p></div>';
		}

		inner += navRow({
			act: "cleaner",
			icon: "trash",
			title: "Broken Media Cleaner",
			desc: "Find and remove unlinked media files",
			value: state.cleanerState === "scanning" ? "Scanning..." : "Scan",
		});

		inner += "</div>";

		var backups = backupLog.length;
		var missingCount = missing;
		return groupHtml(
			"Data Management",
			[
				{ icon: "file", label: "Backups", value: backups },
				{ icon: "download", label: "Missing", value: missingCount },
			],
			inner,
		);
	}

	function aboutSection() {
		var inner = '<div style="padding:6px 0">';

		inner += infoRow({
			icon: "info",
			title: "MediaNest App",
			desc: "A premium offline-first media manager and subscription player.",
		});
		inner += infoRow({ icon: "device", title: "Version", value: "v1.0.0" });
		inner += infoRow({ icon: "edit", title: "Author", value: "Kushal" });

		inner += divider();

		inner += '<div style="padding:14px 16px">';
		inner += '<span class="mn-key">App Updates</span>';
		inner +=
			'<p class="mn-muted" style="font-size:12px;margin:4px 0 10px">Current installed version: v1.0.0</p>';
		inner += "</div>";

		inner += navRow({
			act: "auto-check",
			icon: "refresh",
			title: "Auto-check for updates",
			value: autoCheckLabel(state.autoCheckInterval),
		});

		inner += '<div style="padding:0 16px 16px">';
		inner +=
			'<button class="mn-btn mn-btn--primary mn-btn--block" data-act="check-updates" ' +
			(state.updateState === "checking" || state.updateState === "downloading"
				? "disabled"
				: "") +
			">" +
			(state.updateState === "checking"
				? '<span class="mn-spinner"></span>'
				: icon("refresh", "mn-icon--sm")) +
			" Check for Updates</button>";
		inner += "</div>";

		if (state.updateState === "checking") {
			inner +=
				'<div style="padding:0 16px 16px" class="mn-row mn-gap-2"><span class="mn-spinner"></span><span class="mn-muted" style="font-size:13px">Checking GitHub releases...</span></div>';
		} else if (state.updateState === "available") {
			inner += '<div style="padding:0 16px 16px">';
			inner +=
				'<div class="mn-card mn-card--raised mn-card--pad" style="margin-bottom:10px">' +
				'<p style="margin:0 0 4px;font-weight:600">New Version Available: v2.1.0</p>' +
				'<p class="mn-muted" style="font-size:12px;margin:0">Faster extraction, new glassmorphism UI, playlist autoplay, and several download reliability fixes.</p>' +
				"</div>";
			inner +=
				'<button class="mn-btn mn-btn--primary mn-btn--block" data-act="download-update">' +
				icon("download", "mn-icon--sm") +
				" Download &amp; Install</button>";
			inner += "</div>";
		} else if (state.updateState === "downloading") {
			inner +=
				'<div style="padding:0 16px 16px">' +
				'<p class="mn-muted" style="font-size:13px;margin:0 0 6px">Downloading update: ' +
				state.updateProgress +
				"%</p>" +
				'<div class="mn-bar"><span style="width:' +
				state.updateProgress +
				'%"></span></div>' +
				"</div>";
		} else if (state.updateState === "ready") {
			inner +=
				'<div style="padding:0 16px 16px">' +
				'<p style="font-size:13px;margin:0 0 10px">Update downloaded and ready to install.</p>' +
				'<button class="mn-btn mn-btn--primary mn-btn--block" data-act="install">' +
				icon("check-circle", "mn-icon--sm") +
				" Install Update</button>" +
				"</div>";
		}

		inner += divider();
		inner += navRow({
			act: "notifications",
			icon: "bell",
			title: "Notifications",
			desc: "Download, subscription and sync events",
			value: String(DATA.notifications.length),
		});

		inner += "</div>";
		return groupHtml(
			"About &amp; Updates",
			[
				{
					icon: "bell",
					label: "Notifications",
					value: DATA.notifications.length,
				},
				{ icon: "device", label: "Version", value: "v1.0.0" },
			],
			inner,
		);
	}

	function autoBackupLabel(v) {
		if (v === 0) return "Off";
		if (v === 168) return "Every 7 days";
		return "Every " + v + "h";
	}
	function autoCheckLabel(v) {
		if (v === 0) return "Off (manual only)";
		if (v === 168) return "Every 7 days";
		return "Every 24 hours";
	}

	/* ----- full render ------------------------------------------------------- */

	function html() {
		return (
			'<div style="padding-bottom:8px">' +
			syncSection() +
			downloadsSection() +
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
			state.syncMessage = "Registering device...";
			render(el);
			later(() => {
				state.deviceId = "mn-device-4f2a";
				state.syncState = "success";
				state.syncMessage = "Device registered";
				state.lastSyncAt = Date.now();
				MN.toast("Device registered", "success");
				render(el);
			}, 1500);
			return;
		}

		if (act === "sync") {
			state.syncState = "syncing";
			state.syncMessage = "Syncing with VPS...";
			render(el);
			later(() => {
				state.syncState = "success";
				state.syncMessage = "Sync complete";
				state.lastSyncAt = Date.now();
				syncLog.unshift({
					type: "pull",
					table: "history",
					summary: "Pulled latest changes",
					time: Date.now(),
				});
				MN.toast("Sync complete", "success");
				MN.notify({
					type: "success",
					title: "Sync complete",
					desc: "History & subscriptions synced across devices.",
					channel: "VPS Sync",
				});
				render(el);
			}, 1800);
			return;
		}

		if (act === "interval") {
			openSelect(
				"Auto-sync interval",
				[
					{ value: 1, label: "Every 1 hour" },
					{ value: 2, label: "Every 2 hours" },
					{ value: 6, label: "Every 6 hours" },
					{ value: 12, label: "Every 12 hours" },
					{ value: 24, label: "Every 24 hours" },
				],
				state.syncInterval,
				(v) => {
					state.syncInterval = parseInt(v, 10);
					render(el);
					MN.toast("Auto-sync set to every " + v + "h", "info");
				},
			);
			return;
		}

		if (act === "toggle-log") {
			state.logExpanded = !state.logExpanded;
			render(el);
			return;
		}

		if (act === "location") {
			MN.sheet({
				title: "Download Location",
				body:
					'<p class="mn-muted" style="font-size:12.5px;margin:0 0 10px">Select where media files should be saved.</p>' +
					'<div class="mn-field"><span>' +
					icon("folder", "mn-icon--sm") +
					"</span>" +
					'<input id="inp-location" type="text" value="' +
					MN.esc(state.downloadLocation) +
					'"/></div>' +
					'<button class="mn-btn mn-btn--primary mn-btn--block" id="btn-apply-location" style="margin-top:14px">' +
					icon("check", "mn-icon--sm") +
					" Apply Location</button>",
				onOpen: (bodyEl) => {
					var inp = bodyEl.querySelector("#inp-location");
					bodyEl.querySelector("#btn-apply-location").onclick = () => {
						state.downloadLocation = inp.value.trim() || state.downloadLocation;
						MN.closeSheet();
						render(el);
						MN.toast("Location updated", "success");
					};
				},
			});
			return;
		}

		if (act === "default-res") {
			openSelect(
				"Default Download Resolution",
				[
					{ value: "1080p", label: "1080p (Full HD)" },
					{ value: "720p", label: "720p (HD)" },
					{ value: "480p", label: "480p (SD)" },
					{ value: "360p", label: "360p (Low data - Default)" },
					{ value: "Audio", label: "Audio Only" },
				],
				state.defaultResolution,
				(v) => {
					state.defaultResolution = v;
					MN.store.set({ defaultResolution: v });
					render(el);
					MN.toast("Default resolution: " + v, "info");
				},
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
					"Metadata Only",
					"Database and preferences (small ZIP).",
					true,
				) +
				radioOption(
					"full",
					"Full Backup",
					"Includes all downloaded video and audio files.",
					false,
				);
			MN.dialog({
				title: "Export Backup",
				body: body,
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Export",
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
				title: "Import Backup",
				body:
					"<p>Choose a ZIP backup to restore. This will overwrite current library records.</p>",
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Choose File",
						cls: "mn-btn--primary",
						onClick: () => {
							MN.toast("Import flow would open a file picker", "info");
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
					{ value: 0, label: "Disabled (Off)" },
					{ value: 6, label: "Every 6 hours" },
					{ value: 12, label: "Every 12 hours" },
					{ value: 24, label: "Every 24 hours" },
					{ value: 168, label: "Every 7 days" },
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
					"<p>Restore from <strong>" +
					MN.esc(arg) +
					"</strong>? This will overwrite your current library database records.</p>",
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Restore",
						cls: "mn-btn--primary",
						onClick: () => {
							MN.toast("Backup restored", "success");
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
					"<p>Delete <strong>" +
					MN.esc(arg) +
					"</strong>? This cannot be undone.</p>",
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Delete",
						cls: "mn-btn--danger-solid",
						onClick: () => {
							backupLog = backupLog.filter((b) => b.name !== arg);
							render(el);
							MN.toast("Backup deleted", "info");
						},
					},
				],
			});
			return;
		}

		if (act === "redownload") {
			MN.dialog({
				title: "Download Missing Files",
				body: "<p>Re-queue completed files that are absent on disk?</p>",
				actions: [
					{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
					{
						label: "Re-download",
						cls: "mn-btn--primary",
						onClick: () => {
							MN.toast("Missing files queued", "success");
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
					title: "Library Repair",
					body:
						'<p class="mn-success" style="margin:0 0 6px">Repair complete</p><p>Fixed 2 incorrect paths, cleared 1 missing offline status.</p>',
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
					{ value: 0, label: "Off (Manual only)" },
					{ value: 24, label: "Every 24 hours (daily)" },
					{ value: 168, label: "Every 7 days" },
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

		if (act === "install") {
			MN.dialog({
				title: "Install Update",
				body: "<p>Installing v2.1.0... the app will restart.</p>",
				actions: [
					{
						label: "OK",
						cls: "mn-btn--primary",
						onClick: () => {
							state.updateState = "idle";
							render(el);
							MN.toast("Update installed", "success");
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
			MN.toast("Exporting full backup...", "info");
			later(() => {
				MN.toast("Full backup exported", "success");
			}, 1200);
		} else {
			MN.toast("Metadata backup exported", "success");
		}
	}

	function showRepairDetails() {
		MN.dialog({
			title: "Library Repair Details",
			body:
				'<div class="mn-list mn-list--dense">' +
				'<p style="margin:0;font-weight:600">Fixed paths</p>' +
				'<p class="mn-muted" style="margin:0;font-size:12.5px">• vid_1001 → corrected absolute path</p>' +
				'<p class="mn-muted" style="margin:0;font-size:12.5px">• vid_1003 → corrected absolute path</p>' +
				'<p style="margin:8px 0 0;font-weight:600">Cleared offline status</p>' +
				'<p class="mn-muted" style="margin:0;font-size:12.5px">• vid_1005 — file missing on disk</p>' +
				"</div>",
			actions: [{ label: "Close", cls: "mn-btn--primary", onClick: () => {} }],
		});
	}

	function showCleanerResults(el) {
		var files = [
			{ name: "vid_1007.tmp.mp4", sizeBytes: 84000000, isAudio: false },
			{ name: "vid_1009.part.m4a", sizeBytes: 1200000, isAudio: true },
		];
		var total = files.reduce((a, b) => a + b.sizeBytes, 0);
		var body =
			'<p style="margin:0 0 8px">Found ' +
			files.length +
			" broken files (" +
			MN.fmt.bytes(total) +
			").</p>" +
			'<div class="mn-list mn-list--dense">';
		files.forEach((f) => {
			body +=
				'<div class="mn-row mn-gap-2" style="padding:6px 0">' +
				'<span class="mn-muted">' +
				icon(f.isAudio ? "music" : "video", "mn-icon--sm") +
				"</span>" +
				'<span class="mn-fill" style="font-size:12.5px">' +
				MN.esc(f.name) +
				"</span>" +
				'<span class="mn-muted" style="font-size:11px">' +
				MN.fmt.bytes(f.sizeBytes) +
				"</span>" +
				'<button class="mn-icon-btn mn-icon-btn--sm" style="color:var(--mn-destructive)" aria-label="Delete">' +
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
					label: "Delete All",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						MN.toast("Broken files deleted", "success");
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
