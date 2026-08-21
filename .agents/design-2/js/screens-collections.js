/* ============================================================================
   MediaNest — Collections screen (Library)
   Registers route 'collections'.

   Tabs: history, watched, folders, favorites, playlists, channels.
   - Tabs are NOT sticky (plain scrolling content).
   - Per-tab scoped search (no global search).
   - Each tab shows a statistics line.
   - Card actions collapse into a single three-dot (more) sheet.
   - List/grid toggle in the appbar works across the whole tab.
   ========================================================================== */

(() => {
	var MN = window.MN;
	var DATA = MN.DATA;

	/* ---------------------------------------------------------------------------
     Local screen state (survives re-mounts within the session)
     ------------------------------------------------------------------------- */
	var state = {
		tab: "history", // history | watched | folders | favorites | playlists | channels
		queries: {}, // per-tab search strings (scoped to each tab)
		sortCat: "date", // date | name | duration
		sortDir: "desc", // desc | asc
		view: "list", // list | grid
		folderStack: [], // array of folder ids for breadcrumb navigation (root = [])
		selectionMode: false,
		selectedIds: {}, // map videoId -> true
		visibleCounts: {}, // per-tab visible item count
		selectedSubId: null,
		subSaved: {},
		subSubscribed: {},
		showShorts: false,
	};
	var titleExpanded = new Set();

	function getVisibleCount() {
		if (!state.visibleCounts) state.visibleCounts = {};
		if (typeof state.visibleCounts[state.tab] !== "number") {
			state.visibleCounts[state.tab] = 10;
		}
		return state.visibleCounts[state.tab];
	}

	function setVisibleCount(val) {
		if (!state.visibleCounts) state.visibleCounts = {};
		state.visibleCounts[state.tab] = val;
	}

	function getTotalItems() {
		if (state.tab === "history") {
			var cwCount = DATA.videos.filter(
				(v) => progressFraction(v) > 0 && progressFraction(v) < 0.95,
			).length;
			var vidsCount = visibleVideos().length;
			return Math.max(cwCount, vidsCount);
		}
		if (state.tab === "watched" || state.tab === "favorites") {
			return visibleVideos().length;
		}
		if (state.tab === "folders") {
			var folderId = currentFolderId();
			var q = currentQuery();
			var folders = folderId == null ? rootFolders() : childFolders(folderId);
			var vids = folderVideos();
			if (q && folderId == null) {
				folders = folders.filter((f) => f.name.toLowerCase().includes(q));
			}
			if (q) {
				vids = vids.filter(
					(v) =>
						v.title.toLowerCase().includes(q) ||
						v.channelName.toLowerCase().includes(q),
				);
			}
			return Math.max(folders.length, vids.length);
		}
		if (state.tab === "playlists" || state.tab === "channels") {
			if (state.selectedSubId) {
				var subObj = getSelectedSub();
				if (subObj) {
					var subVids = getSubVideos(subObj);
					return subVids.length;
				}
			}
			var type = state.tab === "playlists" ? "playlist" : "channel";
			var q = currentQuery();
			var subs = DATA.subscriptions.filter(
				(s) => s.sourceType === type && (!q || s.name.toLowerCase().includes(q)),
			);
			return subs.length;
		}
		return 0;
	}

	var TABS = [
		{ id: "history", label: "History", icon: "history" },
		{ id: "watched", label: "Watched", icon: "watched" },
		{ id: "folders", label: "Folders", icon: "folder" },
		{ id: "favorites", label: "Favorites", icon: "heart" },
		{ id: "playlists", label: "Playlists", icon: "playlist" },
		{ id: "channels", label: "Channels", icon: "channel" },
	];

	var SORTS = [
		{ id: "date", label: "Date" },
		{ id: "name", label: "Name" },
		{ id: "duration", label: "Duration" },
	];

	/* ---------------------------------------------------------------------------
     Data helpers
     ------------------------------------------------------------------------- */
	function findVideo(id) {
		var v = DATA.videos.find((v) => v.id === id);
		if (v) return v;
		var allSubVids = (
			DATA.playlist && DATA.playlist.videos ? DATA.playlist.videos : []
		).concat(DATA.channel && DATA.channel.uploads ? DATA.channel.uploads : []);
		var pv = allSubVids.find((x) => x.id === id);
		if (pv) return enrichSubVideo(pv);
		return null;
	}

	function historyFor(id) {
		return DATA.history.find((h) => h.videoId === id);
	}

	function progressFraction(v) {
		var h = historyFor(v.id);
		if (!h || h.positionMillis <= 0 || v.durationSeconds <= 0) return 0;
		var f = h.positionMillis / 1000 / v.durationSeconds;
		if (f > 1) f = 1;
		return f;
	}

	function foldersFor(id) {
		return DATA.videoFolderMap[id] || [];
	}

	function currentQuery() {
		return (state.queries[state.tab] || "").trim().toLowerCase();
	}

	/* returns the full (unfiltered-by-search) video list for the active tab */
	function tabVideos() {
		if (state.tab === "history") {
			return DATA.videos.filter((v) =>
				DATA.history.some((h) => h.videoId === v.id),
			);
		}
		if (state.tab === "watched") {
			return DATA.videos.filter((v) => v.watchCount > 0);
		}
		if (state.tab === "favorites") {
			return DATA.videos.filter((v) => v.favorite);
		}
		if (state.tab === "folders") {
			return folderVideos();
		}
		return [];
	}

	/* applies the tab-scoped query + sort to the active tab's videos */
	function visibleVideos() {
		var q = currentQuery();
		var list = tabVideos();

		if (q) {
			list = list.filter(
				(v) =>
					v.title.toLowerCase().includes(q) ||
					v.channelName.toLowerCase().includes(q),
			);
		}

		return sortVideos(list);
	}

	function folderVideos() {
		var folderId = currentFolderId();
		if (folderId == null) return [];
		return DATA.videos.filter((v) => {
			var folders = foldersFor(v.id);
			return folders.some((f) => f.id === folderId);
		});
	}

	function currentFolderId() {
		return state.folderStack.length
			? state.folderStack[state.folderStack.length - 1]
			: null;
	}

	function rootFolders() {
		return DATA.folders.filter((f) => f.parentId == null);
	}

	function childFolders(parentId) {
		return DATA.folders.filter((f) => f.parentId === parentId);
	}

	function sortVideos(list) {
		var arr = list.slice();
		var cat = state.sortCat;
		var dir = state.sortDir;
		arr.sort((a, b) => {
			var res = 0;
			if (cat === "name") {
				res = (a.title || "").localeCompare(b.title || "");
			} else if (cat === "duration") {
				res = (a.durationSeconds || 0) - (b.durationSeconds || 0);
			} else {
				res = (a.addedAt || 0) - (b.addedAt || 0);
			}
			return dir === "asc" ? res : -res;
		});
		return arr;
	}

	function sortSubscriptions(list) {
		var arr = list.slice();
		var cat = state.sortCat;
		var dir = state.sortDir;
		arr.sort((a, b) => {
			var res = 0;
			if (cat === "name") {
				res = (a.name || "").localeCompare(b.name || "");
			} else if (cat === "duration") {
				res =
					(a.durationSeconds || a.duration || 0) -
					(b.durationSeconds || b.duration || 0);
				if (res === 0) res = (a.id || 0) - (b.id || 0);
			} else {
				res = (a.id || 0) - (b.id || 0);
			}
			return dir === "asc" ? res : -res;
		});
		return arr;
	}

	function sortLabel() {
		var s = SORTS.find((x) => x.id === state.sortCat);
		var label = s ? s.label : "Date";
		if (state.sortCat === "date") {
			return (
				label + (state.sortDir === "asc" ? " (oldest first)" : " (newest first)")
			);
		}
		if (state.sortCat === "name") {
			return label + (state.sortDir === "desc" ? " (Z-A)" : " (A-Z)");
		}
		if (state.sortCat === "duration") {
			return (
				label + (state.sortDir === "asc" ? " (shortest first)" : " (longest first)")
			);
		}
		return label;
	}

	function sortIcon() {
		return state.sortDir === "asc" ? "arrow-up" : "arrow-down";
	}

	function toggleView() {
		state.view = state.view === "grid" ? "list" : "grid";
		var el = MN.qs("#view-root");
		if (el) render(el);
		MN.refreshAppbar();
	}

	MN.collections = {
		toggleView: toggleView,
	};

	/* ---------------------------------------------------------------------------
     Rendering entry
     ------------------------------------------------------------------------- */
	function mount(el) {
		var storeMode = MN.store.get().collectionsViewMode;
		if (storeMode) {
			state.view = storeMode.toLowerCase() === "grid" ? "grid" : "list";
		}
		render(el);

		function onScroll() {
			var currentCount = getVisibleCount();
			var total = getTotalItems();
			if (currentCount >= total) return;
			var target = el || MN.qs("#view-root");
			if (
				target &&
				target.scrollHeight - target.scrollTop - target.clientHeight < 80
			) {
				setVisibleCount(currentCount + 10);
				render(target);
			}
		}

		var scrollTarget = el || MN.qs("#view-root");
		if (scrollTarget) {
			scrollTarget.addEventListener("scroll", onScroll);
		}

		return {
			unmount: () => {
				if (scrollTarget) {
					scrollTarget.removeEventListener("scroll", onScroll);
				}
			},
		};
	}

	function render(el) {
		var html =
			"" +
			tabsHtml() +
			searchBarHtml() +
			statsLineHtml() +
			(state.tab === "history" ? continueWatchingHtml() : "") +
			contentHtml();

		MN.render(el, html);
		bind(el);
	}

	/* ---------------------------------------------------------------------------
     Sections
     ------------------------------------------------------------------------- */
	function tabsHtml() {
		var html = '<div class="mn-chips mn-coll-tabs">';
		TABS.forEach((t) => {
			var active = t.id === state.tab ? " mn-chip--active" : "";
			html +=
				'<button class="mn-chip' +
				active +
				'" data-tab="' +
				t.id +
				'" title="' +
				MN.esc(t.label) +
				'">' +
				MN.icon(t.icon, "mn-icon--sm") +
				"<span>" +
				MN.esc(t.label) +
				"</span>" +
				"</button>";
		});
		html += "</div>";
		return html;
	}

	/* Per-tab scoped search — only filters the active tab. */
	function searchBarHtml() {
		var q = state.queries[state.tab] || "";
		return (
			"" +
			'<div class="mn-field" style="margin:10px 0 6px">' +
			'<span style="color:var(--mn-text-secondary);display:inline-flex">' +
			MN.icon("search") +
			"</span>" +
			'<input id="coll-search" type="text" placeholder="Search ' +
			MN.esc(activeTabLabel().toLowerCase()) +
			'..." value="' +
			MN.esc(q) +
			'" />' +
			(q
				? '<button class="mn-icon-btn mn-icon-btn--sm" id="coll-clear" aria-label="Clear" title="Clear search">' +
					MN.icon("close") +
					"</button>"
				: "") +
			"</div>"
		);
	}

	function activeTabLabel() {
		var t = TABS.find((x) => x.id === state.tab);
		return t ? t.label : "items";
	}

	/* A statistics line describing the current tab's contents. */
	function statsLineHtml() {
		var label = "";
		var iconName = "chart";

		if (state.tab === "history") {
			var histCount = tabVideos().length;
			var totalMs = DATA.history.reduce(
				(sum, h) => sum + (h.totalWatchTimeMillis || 0),
				0,
			);
			label =
				histCount +
				" video" +
				(histCount === 1 ? "" : "s") +
				" · " +
				MN.fmt.duration(Math.round(totalMs / 1000)) +
				" watched";
			iconName = "history";
		} else if (state.tab === "watched") {
			var wCount = tabVideos().length;
			label = wCount + " watched video" + (wCount === 1 ? "" : "s");
			iconName = "watched";
		} else if (state.tab === "favorites") {
			var fCount = tabVideos().length;
			label = fCount + " favorite video" + (fCount === 1 ? "" : "s");
			iconName = "heart";
		} else if (state.tab === "folders") {
			var folderId = currentFolderId();
			var folders = folderId == null ? rootFolders() : childFolders(folderId);
			var vids = folderVideos();
			label =
				folders.length +
				" folder" +
				(folders.length === 1 ? "" : "s") +
				" · " +
				vids.length +
				" video" +
				(vids.length === 1 ? "" : "s");
			iconName = "folder";
		} else if (state.tab === "playlists") {
			var pCount = DATA.subscriptions.filter(
				(s) => s.sourceType === "playlist",
			).length;
			label = pCount + " saved playlist" + (pCount === 1 ? "" : "s");
			iconName = "playlist";
		} else if (state.tab === "channels") {
			var cCount = DATA.subscriptions.filter(
				(s) => s.sourceType === "channel",
			).length;
			label = cCount + " subscribed channel" + (cCount === 1 ? "" : "s");
			iconName = "channel";
		}

		return (
			'<p class="mn-muted mn-row mn-gap-2" style="font-size:var(--mn-fs-meta);margin:0 0 10px">' +
			'<span style="display:inline-flex;color:var(--mn-accent)">' +
			MN.icon(iconName, "mn-icon--sm") +
			"</span>" +
			"<span>" +
			MN.esc(label) +
			"</span>" +
			"</p>"
		);
	}

	function continueWatchingHtml() {
		var list = DATA.videos
			.filter((v) => progressFraction(v) > 0 && progressFraction(v) < 0.95)
			.slice(0, getVisibleCount());
		if (!list.length) return "";
		var html = sectionTitleHtml("Continue watching");
		html += '<div class="mn-scroll-row">';
		list.forEach((v) => {
			html += continueCardHtml(v);
		});
		html += "</div>";
		return html;
	}

	function sectionTitleHtml(title, withSort) {
		var act = "";
		if (withSort) {
			act =
				'<button class="mn-section-title__action mn-row mn-gap-1" data-action="sort" title="Sort videos">' +
				'<span style="display:inline-flex">' +
				MN.icon(sortIcon(), "mn-icon--sm") +
				"</span>" +
				"<span>" +
				MN.esc(sortLabel()) +
				"</span>" +
				"</button>";
		}
		return (
			'<div class="mn-section-title"><h2>' +
			MN.esc(title) +
			"</h2>" +
			act +
			"</div>"
		);
	}

	function contentHtml() {
		if (state.tab === "folders") {
			return foldersContentHtml();
		}
		if (state.tab === "playlists" || state.tab === "channels") {
			return subscriptionsContentHtml();
		}

		var vids = visibleVideos();
		var html = sectionTitleHtml("All videos", true);
		if (vids.length) {
			html += videoCollectionHtml(vids.slice(0, getVisibleCount()), vids.length);
		} else {
			html += emptyStateForTab();
		}
		return html;
	}

	function emptyStateForTab() {
		if (state.tab === "history")
			return MN.stateView(
				"history",
				"No watch history yet",
				"Videos you play will show up here with their progress.",
			);
		if (state.tab === "watched")
			return MN.stateView(
				"watched",
				"No watched videos yet",
				"Mark videos as watched and they will appear here.",
			);
		if (state.tab === "favorites")
			return MN.stateView(
				"heart",
				"No favorite videos",
				"Tap the heart on any video to save it here.",
			);
		return MN.stateView("video", "Nothing here", "No videos match your search.");
	}

	function videoCollectionHtml(vids, totalLength) {
		var html = "";
		if (state.view === "grid") {
			html += '<div class="mn-grid">';
			vids.forEach((v) => {
				html += mediaCardHtml(v);
			});
			html += "</div>";
		} else {
			html += '<div class="mn-list">';
			vids.forEach((v) => {
				html += mediaRowHtml(v);
			});
			html += "</div>";
		}
		if (typeof totalLength === "number" && totalLength > 0) {
			if (getVisibleCount() >= totalLength) {
				html +=
					'<div class="mn-row mn-center mn-muted" style="padding:16px 0 8px;font-size:12px;letter-spacing:0.3px"><span style="opacity:0.6">• You have reached the end of the list •</span></div>';
			} else {
				html +=
					'<div class="mn-row mn-center mn-muted" style="padding:12px 0;font-size:12px">Loading…</div>';
			}
		}
		return html;
	}

	/* ---------------------------------------------------------------------------
     Media row / card
     ------------------------------------------------------------------------- */
	function thumbHtml(v, opts) {
		opts = opts || {};
		var dur = v.durationSeconds > 0 ? MN.fmt.duration(v.durationSeconds) : "";
		var prog = progressFraction(v);
		var downloaded = v.localFilePath ? true : false;
		var badge = "";
		if (downloaded) {
			badge =
				'<span class="mn-thumb__badge mn-thumb__badge--tr mn-thumb__badge--success">' +
				MN.icon("check-circle", "mn-icon--sm") +
				"</span>";
		}
		var watchBadge =
			v.watchCount > 0
				? '<span class="mn-thumb__badge mn-thumb__badge--bl">' +
					MN.icon("eye", "mn-icon--sm") +
					" " +
					v.watchCount +
					"</span>"
				: "";

		var html =
			'<div class="mn-thumb mn-media-row__thumb">' +
			'<img src="' +
			MN.esc(v.thumbnailUrl) +
			'" alt="" />' +
			MN.typeBadge(v) +
			badge +
			watchBadge +
			(dur
				? '<span class="mn-thumb__badge mn-thumb__badge--br">' +
					MN.esc(dur) +
					"</span>"
				: "") +
			(prog > 0
				? '<div class="mn-thumb__progress"><span style="width:' +
					prog * 100 +
					'%"></span></div>'
				: "") +
			"</div>";
		return html;
	}

	function mediaRowHtml(v) {
		var playing = MN.store.get().playing;
		var isCurrent = playing && playing.id === v.id;
		var selected = state.selectionMode && state.selectedIds[v.id];
		var current = isCurrent ? " mn-media-row--current" : "";
		var isExpanded = titleExpanded.has(v.id);
		var titleStyle = isExpanded
			? "display:block;white-space:normal;overflow:visible;-webkit-line-clamp:none;cursor:pointer"
			: "display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;cursor:pointer";

		var nowBadge = isCurrent
			? '<span class="mn-media-row__now">' +
				(MN.store.get().isPlaying
					? MN.icon("play", "mn-icon--sm") + " Now playing"
					: MN.icon("pause", "mn-icon--sm") + " Paused") +
				"</span>"
			: "";

		var html =
			'<div class="mn-media-row' + current + '" data-video="' + v.id + '">';
		if (state.selectionMode) {
			html +=
				'<button class="mn-checkbox' +
				(selected ? " mn-checkbox--checked" : "") +
				'" data-select="' +
				v.id +
				'" aria-label="Select" title="Select video">' +
				MN.icon("check") +
				"</button>";
		}

		// Left column: thumbnail + folder badges underneath
		html +=
			'<div style="display:flex;flex-direction:column;width:128px;flex:0 0 auto;gap:4px">';
		html += thumbHtml(v);
		html += foldersBadgeHtml(v);
		html += "</div>";

		// Right column: body with full width title, nowBadge, tight meta line, and three-dot at bottom-right
		html +=
			'<div class="mn-media-row__body" style="display:flex;flex-direction:column;flex:1;min-width:0;position:relative">' +
			'<p class="mn-media-row__title" data-title-toggle="' +
			v.id +
			'" style="' +
			titleStyle +
			'">' +
			MN.esc(v.title) +
			"</p>" +
			nowBadge +
			'<div class="mn-media-row__meta" style="margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' +
			"<span>" +
			MN.esc(v.channelName) +
			"</span>" +
			"<span>·</span>" +
			"<span>" +
			MN.esc(v.uploadDate) +
			"</span>" +
			"</div>" +
			'<div style="margin-top:auto;display:flex;justify-content:flex-end;align-items:flex-end">' +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-more="' +
			v.id +
			'" aria-label="More options" title="More options" style="margin-top:4px">' +
			MN.icon("more", "mn-icon--sm") +
			"</button>" +
			"</div>" +
			"</div>" +
			"</div>";

		return html;
	}

	function mediaCardHtml(v) {
		var playing = MN.store.get().playing;
		var isCurrent = playing && playing.id === v.id;
		var selected = state.selectionMode && state.selectedIds[v.id];
		var dur = v.durationSeconds > 0 ? MN.fmt.duration(v.durationSeconds) : "";
		var prog = progressFraction(v);
		var isExpanded = titleExpanded.has(v.id);
		var titleStyle = isExpanded
			? "display:block;white-space:normal;overflow:visible;-webkit-line-clamp:none;cursor:pointer"
			: "display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;cursor:pointer";

		var html =
			'<div class="mn-media-card" data-video="' +
			v.id +
			'">' +
			'<div class="mn-card mn-card--interactive">';
		if (state.selectionMode) {
			html +=
				'<div style="position:absolute;top:8px;left:8px;z-index:3"><button class="mn-checkbox' +
				(selected ? " mn-checkbox--checked" : "") +
				'" data-select="' +
				v.id +
				'" aria-label="Select" title="Select video">' +
				MN.icon("check") +
				"</button></div>";
		}
		html +=
			'<div class="mn-thumb mn-media-card__thumb">' +
			'<img src="' +
			MN.esc(v.thumbnailUrl) +
			'" alt="" />' +
			MN.typeBadge(v) +
			(dur
				? '<span class="mn-thumb__badge mn-thumb__badge--br">' +
					MN.esc(dur) +
					"</span>"
				: "") +
			(prog > 0
				? '<div class="mn-thumb__progress"><span style="width:' +
					prog * 100 +
					'%"></span></div>'
				: "") +
			(isCurrent
				? '<div class="mn-thumb__play">' + MN.icon("play") + "</div>"
				: "") +
			"</div>" +
			'<div class="mn-media-card__body">' +
			'<p class="mn-media-card__title" data-title-toggle="' +
			v.id +
			'" style="' +
			titleStyle +
			'">' +
			MN.esc(v.title) +
			"</p>" +
			'<div class="mn-media-card__meta">' +
			MN.esc(v.channelName) +
			"</div>" +
			'<div class="mn-media-card__actions">' +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-more="' +
			v.id +
			'" aria-label="More options" title="More options">' +
			MN.icon("more", "mn-icon--sm") +
			"</button>" +
			"</div>" +
			"</div>" +
			"</div></div>";
		return html;
	}

	function continueCardHtml(v) {
		var prog = progressFraction(v);
		var pos = historyFor(v);
		var posLabel =
			pos && pos.positionMillis > 0
				? "Left off at " + MN.fmt.duration(pos.positionMillis / 1000)
				: "";
		return (
			'<div class="mn-media-card" style="width:220px;flex:0 0 auto" data-video="' +
			v.id +
			'">' +
			'<div class="mn-card mn-card--interactive">' +
			'<div class="mn-thumb mn-media-card__thumb">' +
			'<img src="' +
			MN.esc(v.thumbnailUrl) +
			'" alt="" />' +
			MN.typeBadge(v) +
			'<div class="mn-thumb__progress"><span style="width:' +
			prog * 100 +
			'%"></span></div>' +
			'<div class="mn-thumb__play">' +
			MN.icon("play") +
			"</div>" +
			"</div>" +
			'<div class="mn-media-card__body">' +
			'<p class="mn-media-card__title">' +
			MN.esc(v.title) +
			"</p>" +
			'<div class="mn-media-card__meta">' +
			MN.esc(posLabel) +
			"</div>" +
			"</div>" +
			"</div></div>"
		);
	}

	function foldersBadgeHtml(v) {
		var folders = foldersFor(v.id);
		if (!folders.length) return "";
		var first = folders[0];
		var html =
			'<div class="mn-row mn-gap-1" style="margin-top:2px;align-items:center;flex-wrap:nowrap;min-width:0">' +
			'<span class="mn-tag" style="max-width:140px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:0 1 auto;min-width:0">' +
			MN.icon("folder", "mn-icon--sm") +
			'<span class="mn-truncate">' +
			MN.esc(first.name) +
			"</span>" +
			"</span>";
		if (folders.length > 1) {
			html +=
				'<button class="mn-tag" data-folders-more="' +
				MN.esc(v.id) +
				'" title="Show all folders" style="cursor:pointer;flex:0 0 auto">+' +
				(folders.length - 1) +
				"</button>";
		}
		html += "</div>";
		return html;
	}

	/* ---------------------------------------------------------------------------
     Folders
     ------------------------------------------------------------------------- */
	function foldersContentHtml() {
		var folderId = currentFolderId();
		var q = currentQuery();
		var html = "";

		// breadcrumb
		html +=
			'<div class="mn-row mn-gap-2" style="margin-bottom:8px;overflow-x:auto">';
		html +=
			'<button class="mn-chip' +
			(folderId == null ? " mn-chip--active" : "") +
			'" data-crumb="" title="All folders">All folders</button>';
		state.folderStack.forEach((id, i) => {
			var f = DATA.folders.find((x) => x.id === id);
			if (!f) return;
			html += MN.icon("chevron-right", "mn-icon--sm");
			html +=
				'<button class="mn-chip' +
				(i === state.folderStack.length - 1 ? " mn-chip--active" : "") +
				'" data-crumb="' +
				i +
				'" title="' +
				MN.esc(f.name) +
				'">' +
				MN.esc(f.name) +
				"</button>";
		});
		html += "</div>";

		// create folder action (root only)
		html +=
			'<div class="mn-row" style="margin-bottom:10px;align-items:center">' +
			(state.selectionMode
				? '<button class="mn-btn mn-btn--sm mn-btn--danger" data-exitselect title="Exit selection mode">Done</button>'
				: "") +
			'<button class="mn-btn mn-btn--sm mn-btn--secondary" data-newfolder title="New folder" style="margin-left:auto">' +
			MN.icon("folder-add", "mn-icon--sm") +
			" New folder</button>" +
			"</div>";

		var folders = folderId == null ? rootFolders() : childFolders(folderId);
		var vids = folderVideos();

		if (q && folderId == null) {
			folders = folders.filter((f) => f.name.toLowerCase().includes(q));
		}
		if (q) {
			vids = vids.filter(
				(v) =>
					v.title.toLowerCase().includes(q) ||
					v.channelName.toLowerCase().includes(q),
			);
		}

		var totalLength = Math.max(folders.length, vids.length);

		// subfolder rows
		var visibleLimit = getVisibleCount();
		if (folders.length) {
			if (state.view === "grid") {
				html += '<div class="mn-grid" style="margin-bottom:12px">';
				folders.slice(0, visibleLimit).forEach((f) => {
					var count = DATA.videos.filter((v) =>
						foldersFor(v.id).some((x) => x.id === f.id),
					).length;
					html += folderGridCardHtml(f, count);
				});
				html += "</div>";
			} else {
				html += '<div class="mn-list" style="margin-bottom:10px">';
				folders.slice(0, visibleLimit).forEach((f) => {
					var count = DATA.videos.filter((v) =>
						foldersFor(v.id).some((x) => x.id === f.id),
					).length;
					html += folderRowHtml(f, count);
				});
				html += "</div>";
			}
		}

		// videos
		if (vids.length) {
			html += sectionTitleHtml("Videos", true);
			html += videoCollectionHtml(sortVideos(vids).slice(0, visibleLimit));
		} else if (!folders.length) {
			html += MN.stateView(
				"folder",
				"Folder is empty",
				"Add videos to this folder or create a subfolder.",
			);
		}

		if (totalLength > 0) {
			if (getVisibleCount() >= totalLength) {
				html +=
					'<div class="mn-row mn-center mn-muted" style="padding:16px 0 8px;font-size:12px;letter-spacing:0.3px"><span style="opacity:0.6">• You have reached the end of the list •</span></div>';
			} else {
				html +=
					'<div class="mn-row mn-center mn-muted" style="padding:12px 0;font-size:12px">Loading…</div>';
			}
		}

		return html;
	}

	function folderGridCardHtml(f, count) {
		return (
			'<div class="mn-media-card" data-folder="' +
			f.id +
			'">' +
			'<div class="mn-card mn-card--interactive mn-card--pad" style="height:100%;display:flex;flex-direction:column;gap:8px;padding:12px">' +
			'<div class="mn-row mn-between">' +
			'<div style="width:40px;height:40px;border-radius:var(--mn-r-md);background:var(--mn-accent-subtle);color:var(--mn-accent);display:flex;align-items:center;justify-content:center">' +
			MN.icon("folder") +
			"</div>" +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-more="folder:' +
			f.id +
			'" aria-label="Folder options" title="Folder options">' +
			MN.icon("more", "mn-icon--sm") +
			"</button>" +
			"</div>" +
			'<div style="margin-top:auto">' +
			'<div class="mn-setting-row__title" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' +
			MN.esc(f.name) +
			"</div>" +
			'<div class="mn-setting-row__desc">' +
			count +
			" video" +
			(count === 1 ? "" : "s") +
			"</div>" +
			"</div>" +
			"</div></div>"
		);
	}

	function folderRowHtml(f, count) {
		return (
			'<div class="mn-card mn-card--interactive" style="padding:10px 14px;margin-bottom:8px" data-folder="' +
			f.id +
			'">' +
			'<div class="mn-row mn-gap-3" style="align-items:flex-start">' +
			'<div style="width:38px;height:38px;border-radius:var(--mn-r-md);background:var(--mn-accent-subtle);color:var(--mn-accent);display:flex;align-items:center;justify-content:center;flex:0 0 auto">' +
			MN.icon("folder") +
			"</div>" +
			'<div class="mn-fill" style="min-width:0">' +
			'<div class="mn-setting-row__title" style="white-space:normal;word-break:break-word">' +
			MN.esc(f.name) +
			"</div>" +
			'<div class="mn-row mn-between" style="align-items:center;margin-top:2px">' +
			'<div class="mn-setting-row__desc">' +
			count +
			" video" +
			(count === 1 ? "" : "s") +
			"</div>" +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-more="folder:' +
			f.id +
			'" aria-label="Folder options" title="Folder options">' +
			MN.icon("more", "mn-icon--sm") +
			"</button>" +
			"</div>" +
			"</div>" +
			"</div>" +
			"</div>"
		);
	}

	/* ---------------------------------------------------------------------------
     Subscriptions (playlists / channels)
     ------------------------------------------------------------------------- */
	function enrichSubVideo(pv) {
		var full = DATA.videos.find((v) => v.title === pv.title || v.id === pv.id);
		if (full) return full;
		return {
			id: pv.id,
			title: pv.title,
			channelName: pv.channelName || "",
			durationSeconds: pv.durationSeconds || 0,
			thumbnailUrl: pv.thumbnailUrl,
			uploadDate: pv.uploadDate || "",
			favorite: false,
			watchCount: pv.watchCount || 0,
			resolution: "720p",
			localFilePath: "",
			addedAt: Date.now(),
		};
	}

	function getSelectedSub() {
		if (!state.selectedSubId) return null;
		return DATA.subscriptions.find((x) => x.id === state.selectedSubId) || null;
	}

	function getSubVideos(s) {
		if (!s) return [];
		var isPlaylist = s.sourceType === "playlist";
		if (isPlaylist) {
			if (DATA.playlist && s.sourceId === DATA.playlist.playlistId) {
				return DATA.playlist.videos || [];
			}
			return DATA.playlist ? DATA.playlist.videos || [] : [];
		} else {
			if (DATA.channel && s.sourceId === DATA.channel.channelId) {
				return DATA.channel.uploads || [];
			}
			return DATA.channel ? DATA.channel.uploads || [] : [];
		}
	}

	function subDetailContentHtml(s) {
		var isPlaylist = s.sourceType === "playlist";
		var backHtml =
			'<div style="margin-bottom:12px">' +
			'<button class="mn-btn mn-btn--sm mn-btn--ghost" data-sub-back title="Back">' +
			MN.icon("back") +
			" Back to " +
			MN.esc(isPlaylist ? "Playlists" : "Channels") +
			"</button>" +
			"</div>";

		var headerCardHtml = "";
		if (isPlaylist) {
			var pl =
				DATA.playlist && s.sourceId === DATA.playlist.playlistId
					? DATA.playlist
					: {
							playlistId: s.sourceId,
							name: s.name,
							thumbnailUrl:
								s.thumbnailUrl || (DATA.playlist ? DATA.playlist.thumbnailUrl : ""),
							videoCount: DATA.playlist ? DATA.playlist.videoCount : 42,
							description: DATA.playlist ? DATA.playlist.description : "",
							videos: DATA.playlist ? DATA.playlist.videos : [],
						};
			var desc = pl && pl.description ? pl.description : "";
			var descHtml = desc
				? '<p class="mn-muted" style="margin:4px 0 0;font-size:12px">' +
					MN.esc(desc) +
					"</p>"
				: "";
			var coverUrl = pl.thumbnailUrl || DATA.helpers.thumb(12, pl.name);
			var isSaved = state.subSaved[s.id] !== false;

			headerCardHtml =
				'<div class="mn-card mn-card--pad" style="margin-bottom:14px">' +
				'<img style="width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:8px;display:block" src="' +
				MN.esc(coverUrl) +
				'" alt=""/>' +
				'<div style="margin-top:10px">' +
				'<p class="mn-key">Playlist</p>' +
				'<h3 style="margin:2px 0;font-size:17px;font-weight:600">' +
				MN.esc(pl.name) +
				"</h3>" +
				'<p class="mn-muted" style="margin:0">Videos: ' +
				pl.videoCount +
				"</p>" +
				descHtml +
				"</div>" +
				'<div class="mn-row mn-gap-2" style="margin-top:12px">' +
				'<button class="mn-btn mn-btn--sm ' +
				(isSaved ? "mn-btn--secondary" : "mn-btn--primary") +
				' mn-fill" data-act="toggle-save" title="' +
				(isSaved ? "Remove from Playlist" : "Save Playlist") +
				'">' +
				MN.icon(isSaved ? "check" : "playlist") +
				(isSaved ? " Saved to Playlist" : " Add to Playlist") +
				"</button>" +
				'<button class="mn-btn mn-btn--sm mn-btn--deep mn-fill" data-act="download-all" title="Download all playlist videos">' +
				MN.icon("download") +
				" Download All</button>" +
				"</div>" +
				'<div class="mn-row mn-between" style="margin-top:12px">' +
				'<span class="mn-muted" style="font-size:12px">Show Shorts</span>' +
				'<button class="mn-switch ' +
				(state.showShorts ? "mn-switch--on" : "") +
				'" data-act="toggle-shorts" role="switch" aria-checked="' +
				(state.showShorts ? "true" : "false") +
				'" title="Toggle Shorts"></button>' +
				"</div>" +
				"</div>";
		} else {
			var ch =
				DATA.channel && s.sourceId === DATA.channel.channelId
					? DATA.channel
					: {
							channelId: s.sourceId,
							name: s.name,
							avatarUrl:
								s.thumbnailUrl || (DATA.channel ? DATA.channel.avatarUrl : ""),
							videoCount: DATA.channel ? DATA.channel.videoCount : 214,
							description: DATA.channel ? DATA.channel.description : "",
							uploads: DATA.channel ? DATA.channel.uploads : [],
						};
			var desc = ch && ch.description ? ch.description : "";
			var descHtml = desc
				? '<p class="mn-muted" style="margin:4px 0 0;font-size:12px">' +
					MN.esc(desc) +
					"</p>"
				: "";
			var bannerUrl = DATA.helpers.thumb(2, ch.name);
			var isSubscribed = state.subSubscribed[s.id] !== false;

			headerCardHtml =
				'<div class="mn-card mn-card--pad" style="margin-bottom:14px">' +
				'<img style="width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:8px;display:block" src="' +
				MN.esc(bannerUrl) +
				'" alt=""/>' +
				'<div class="mn-row mn-gap-3" style="margin-top:10px;align-items:center">' +
				'<img style="width:52px;height:52px;border-radius:50%;object-fit:cover" src="' +
				MN.esc(ch.avatarUrl) +
				'" alt=""/>' +
				"<div>" +
				'<p class="mn-key">Channel</p>' +
				'<h3 style="margin:2px 0;font-size:17px;font-weight:600">' +
				MN.esc(ch.name) +
				"</h3>" +
				'<p class="mn-muted" style="margin:0">Videos: ' +
				ch.videoCount +
				"</p>" +
				"</div>" +
				"</div>" +
				descHtml +
				'<div class="mn-row mn-gap-2" style="margin-top:12px">' +
				'<button class="mn-btn mn-btn--sm ' +
				(isSubscribed ? "mn-btn--secondary" : "mn-btn--deep") +
				' mn-fill" data-act="subscribe" title="' +
				(isSubscribed ? "Unsubscribe" : "Subscribe") +
				'">' +
				MN.icon(isSubscribed ? "check" : "youtube") +
				(isSubscribed ? " Subscribed" : " Subscribe") +
				"</button>" +
				'<button class="mn-btn mn-btn--sm mn-btn--primary mn-fill" data-act="download-all" title="Download all channel uploads">' +
				MN.icon("download") +
				" Download All</button>" +
				"</div>" +
				'<div class="mn-row mn-between" style="margin-top:12px">' +
				'<span class="mn-muted" style="font-size:12px">Show Shorts</span>' +
				'<button class="mn-switch ' +
				(state.showShorts ? "mn-switch--on" : "") +
				'" data-act="toggle-shorts" role="switch" aria-checked="' +
				(state.showShorts ? "true" : "false") +
				'" title="Toggle Shorts"></button>' +
				"</div>" +
				"</div>";
		}

		var rawVideos = getSubVideos(s);
		var subVideos = state.showShorts
			? rawVideos
			: rawVideos.filter((v) => !v.isShort);
		var q = currentQuery();
		if (q) {
			subVideos = subVideos.filter(
				(v) =>
					v.title.toLowerCase().includes(q) ||
					(v.channelName && v.channelName.toLowerCase().includes(q)),
			);
		}
		var enrichedList = subVideos.map(enrichSubVideo);
		var sortedList = sortVideos(enrichedList);

		var listHtml = "";
		if (sortedList.length) {
			listHtml = videoCollectionHtml(
				sortedList.slice(0, getVisibleCount()),
				sortedList.length,
			);
		} else {
			listHtml = emptyStateForTab();
		}

		return (
			backHtml + headerCardHtml + sectionTitleHtml("Videos", true) + listHtml
		);
	}

	function subscriptionsContentHtml() {
		if (state.selectedSubId) {
			var subObj = DATA.subscriptions.find((x) => x.id === state.selectedSubId);
			if (subObj) {
				return subDetailContentHtml(subObj);
			}
			state.selectedSubId = null;
		}

		var type = state.tab === "playlists" ? "playlist" : "channel";
		var q = currentQuery();
		var subs = DATA.subscriptions.filter(
			(s) => s.sourceType === type && (!q || s.name.toLowerCase().includes(q)),
		);

		if (!subs.length) {
			return MN.stateView(
				type === "playlist" ? "playlist" : "channel",
				"No " + (type === "playlist" ? "playlists" : "channels") + " yet",
				"Save " +
					(type === "playlist" ? "playlists" : "channels") +
					" from Home to see them here.",
			);
		}

		var totalLength = subs.length;
		var sortedSubs = sortSubscriptions(subs);
		var html = sectionTitleHtml(
			type === "playlist" ? "Playlists" : "Channels",
			true,
		);
		if (state.view === "grid") {
			html += '<div class="mn-grid">';
			sortedSubs.slice(0, getVisibleCount()).forEach((s) => {
				html += subscriptionGridCardHtml(s);
			});
			html += "</div>";
		} else {
			html += '<div class="mn-list">';
			sortedSubs.slice(0, getVisibleCount()).forEach((s) => {
				html += subscriptionCardHtml(s);
			});
			html += "</div>";
		}

		if (totalLength > 0) {
			if (getVisibleCount() >= totalLength) {
				html +=
					'<div class="mn-row mn-center mn-muted" style="padding:16px 0 8px;font-size:12px;letter-spacing:0.3px"><span style="opacity:0.6">• You have reached the end of the list •</span></div>';
			} else {
				html +=
					'<div class="mn-row mn-center mn-muted" style="padding:12px 0;font-size:12px">Loading…</div>';
			}
		}

		html +=
			'<p class="mn-muted" style="font-size:var(--mn-fs-mini);text-align:center;margin-top:12px">*Automatically downloads new uploads*</p>';
		return html;
	}

	function subscriptionGridCardHtml(s) {
		var isPlaylist = s.sourceType === "playlist";
		var typeLabel = isPlaylist ? "Playlist" : "Channel";
		return (
			'<div class="mn-media-card" data-sub="' +
			s.id +
			'">' +
			'<div class="mn-card mn-card--interactive" style="padding:10px;height:100%;display:flex;flex-direction:column">' +
			'<div style="position:relative;width:100%;aspect-ratio:16/9;background:var(--mn-surface);border-radius:var(--mn-r-sm);overflow:hidden;display:flex;align-items:center;justify-content:center">' +
			(isPlaylist
				? '<img src="' +
					MN.esc(s.thumbnailUrl) +
					'" alt="" style="width:100%;height:100%;object-fit:cover" />' +
					'<span class="mn-thumb__badge mn-thumb__badge--br">' +
					MN.icon("playlist", "mn-icon--sm") +
					"</span>"
				: '<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%">' +
					'<img src="' +
					MN.esc(s.thumbnailUrl) +
					'" alt="" style="width:64px;height:64px;border-radius:50%;object-fit:cover" />' +
					"</div>") +
			"</div>" +
			'<div class="mn-media-card__body" style="flex:1;display:flex;flex-direction:column;gap:4px">' +
			'<p class="mn-media-card__title" style="width:100%">' +
			MN.esc(s.name) +
			"</p>" +
			'<div class="mn-row mn-between" style="align-items:center;min-height:24px">' +
			'<div class="mn-media-card__meta" style="display:flex;align-items:center;gap:4px">' +
			MN.icon(isPlaylist ? "playlist" : "channel", "mn-icon--sm") +
			" " +
			typeLabel +
			"</div>" +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-more="sub:' +
			s.id +
			'" aria-label="Subscription options" title="Subscription options">' +
			MN.icon("more", "mn-icon--sm") +
			"</button>" +
			"</div>" +
			'<div class="mn-stack mn-gap-1" style="margin-top:auto;padding-top:8px;border-top:1px solid rgba(255,255,255,0.06)">' +
			'<div class="mn-row mn-between">' +
			'<span class="mn-key" style="font-size:11px">Auto-dl</span>' +
			'<button class="mn-switch' +
			(s.autoDownload ? " mn-switch--on" : "") +
			'" data-autodl="' +
			s.id +
			'" role="switch" aria-checked="' +
			(s.autoDownload ? "true" : "false") +
			'" title="Toggle auto-download"></button>' +
			"</div>" +
			'<div class="mn-row mn-between">' +
			'<span class="mn-key" style="font-size:11px">Audio</span>' +
			'<button class="mn-switch' +
			(s.audioOnly ? " mn-switch--on" : "") +
			'" data-audioonly="' +
			s.id +
			'" role="switch" aria-checked="' +
			(s.audioOnly ? "true" : "false") +
			'" title="Toggle audio-only auto-download"></button>' +
			"</div>" +
			"</div>" +
			"</div>" +
			"</div></div>"
		);
	}

	function subscriptionCardHtml(s) {
		var isPlaylist = s.sourceType === "playlist";
		var radius = isPlaylist ? "var(--mn-r-sm)" : "50%";
		var typeLabel = isPlaylist ? "Playlist" : "Channel";
		return (
			'<div class="mn-card mn-card--interactive mn-card--pad" data-sub="' +
			s.id +
			'">' +
			'<div class="mn-row mn-gap-3" style="align-items:flex-start">' +
			'<img src="' +
			MN.esc(s.thumbnailUrl) +
			'" alt="" style="width:52px;height:52px;border-radius:' +
			radius +
			';object-fit:cover;flex:0 0 auto" />' +
			'<div class="mn-fill" style="min-width:0">' +
			'<div class="mn-setting-row__title" style="white-space:normal;word-break:break-word">' +
			MN.esc(s.name) +
			"</div>" +
			'<div class="mn-row mn-between" style="align-items:center;margin-top:4px">' +
			'<div class="mn-setting-row__desc" style="display:flex;align-items:center;gap:4px">' +
			MN.icon(isPlaylist ? "playlist" : "channel", "mn-icon--sm") +
			" " +
			typeLabel +
			"</div>" +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-more="sub:' +
			s.id +
			'" aria-label="Subscription options" title="Subscription options">' +
			MN.icon("more", "mn-icon--sm") +
			"</button>" +
			"</div>" +
			"</div>" +
			"</div>" +
			'<div class="mn-row mn-between" style="margin-top:10px">' +
			'<div class="mn-row mn-gap-2">' +
			'<span class="mn-key">Auto-download</span>' +
			'<button class="mn-switch' +
			(s.autoDownload ? " mn-switch--on" : "") +
			'" data-autodl="' +
			s.id +
			'" role="switch" aria-checked="' +
			(s.autoDownload ? "true" : "false") +
			'" title="Toggle auto-download"></button>' +
			"</div>" +
			'<div class="mn-row mn-gap-2">' +
			'<span class="mn-key">Audio-only</span>' +
			'<button class="mn-switch' +
			(s.audioOnly ? " mn-switch--on" : "") +
			'" data-audioonly="' +
			s.id +
			'" role="switch" aria-checked="' +
			(s.audioOnly ? "true" : "false") +
			'" title="Toggle audio-only auto-download"></button>' +
			"</div>" +
			"</div>" +
			"</div>"
		);
	}

	/* ---------------------------------------------------------------------------
     Binding
     ------------------------------------------------------------------------- */
	function bind(el) {
		// per-tab search
		var search = MN.qs("#coll-search", el);
		if (search) {
			search.oninput = () => {
				state.queries[state.tab] = search.value;
				setVisibleCount(10);
				render(el);
			};
			var clear = MN.qs("#coll-clear", el);
			if (clear)
				clear.onclick = () => {
					state.queries[state.tab] = "";
					setVisibleCount(10);
					render(el);
				};
		}

		// tabs
		el.querySelectorAll("[data-tab]").forEach((b) => {
			b.onclick = () => {
				state.tab = b.getAttribute("data-tab");
				state.selectedSubId = null;
				state.folderStack = [];
				state.selectionMode = false;
				state.selectedIds = {};
				setVisibleCount(10);
				render(el);
			};
		});

		// breadcrumb
		el.querySelectorAll("[data-crumb]").forEach((b) => {
			b.onclick = () => {
				var v = b.getAttribute("data-crumb");
				if (v === "") {
					state.folderStack = [];
				} else {
					state.folderStack = state.folderStack.slice(0, parseInt(v, 10) + 1);
				}
				setVisibleCount(10);
				render(el);
			};
		});

		// section actions
		el.querySelectorAll('[data-action="sort"]').forEach((b) => {
			b.onclick = openSortSheet;
		});

		// new folder
		var nf = MN.qs("[data-newfolder]", el);
		if (nf) nf.onclick = openNewFolderDialog;

		// folder open
		el.querySelectorAll("[data-folder]").forEach((b) => {
			b.onclick = (e) => {
				if (e.target.closest("button[data-more]")) return;
				state.folderStack.push(parseInt(b.getAttribute("data-folder"), 10));
				setVisibleCount(10);
				render(el);
			};
		});

		// video interactions (click row/card body -> video detail; more -> sheet)
		el.querySelectorAll("[data-video]").forEach((b) => {
			b.onclick = (e) => {
				if (e.target.closest("button")) return;
				var id = b.getAttribute("data-video");
				var v = findVideo(id);
				if (e.target.closest("[data-title-toggle]")) {
					if (titleExpanded.has(id)) {
						titleExpanded.delete(id);
					} else {
						titleExpanded.add(id);
					}
					render(el);
					return;
				}
				if (state.selectionMode) {
					toggleSelection(id, el);
					return;
				}
				if (!v) return;
				MN.router.navigate("video-detail/" + id);
			};
		});
		el.querySelectorAll("[data-more]").forEach((b) => {
			b.onclick = () => {
				var ref = b.getAttribute("data-more");
				if (ref.slice(0, 7) === "folder:") {
					openFolderMoreSheet(parseInt(ref.slice(7), 10));
				} else if (ref.slice(0, 4) === "sub:") {
					openSubscriptionMoreSheet(parseInt(ref.slice(4), 10));
				} else {
					openMoreSheet(ref);
				}
			};
		});
		el.querySelectorAll("[data-folders-more]").forEach((btn) => {
			btn.onclick = (e) => {
				e.stopPropagation();
				var vid = btn.getAttribute("data-folders-more");
				var list = foldersFor(vid).slice(1);
				var body = "";
				list.forEach((f) => {
					body +=
						'<div class="mn-row mn-gap-2" style="padding:10px 6px">' +
						MN.icon("folder", "mn-icon--sm") +
						'<span class="mn-muted">' +
						MN.esc(f.name) +
						"</span></div>";
				});
				MN.sheet({ title: "Folders", body: body });
			};
		});
		el.querySelectorAll("[data-select]").forEach((b) => {
			b.onclick = () => {
				toggleSelection(b.getAttribute("data-select"), el);
			};
		});
		var exitSel = MN.qs("[data-exitselect]", el);
		if (exitSel)
			exitSel.onclick = () => {
				state.selectionMode = false;
				state.selectedIds = {};
				render(el);
			};

		// sub detail back
		var backBtn = MN.qs("[data-sub-back]", el);
		if (backBtn) {
			backBtn.onclick = () => {
				state.selectedSubId = null;
				setVisibleCount(10);
				render(el);
			};
		}

		// sub header card actions
		var saveBtn = MN.qs('[data-act="toggle-save"]', el);
		if (saveBtn) {
			saveBtn.onclick = () => {
				var subId = state.selectedSubId;
				if (typeof state.subSaved[subId] === "undefined") {
					state.subSaved[subId] = true;
				}
				state.subSaved[subId] = !state.subSaved[subId];
				var isSaved = state.subSaved[subId];
				MN.toast(isSaved ? "Added to Playlist" : "Removed from Playlist", "info");
				render(el);
			};
		}

		var subBtn = MN.qs('[data-act="subscribe"]', el);
		if (subBtn) {
			subBtn.onclick = () => {
				var subId = state.selectedSubId;
				if (typeof state.subSubscribed[subId] === "undefined") {
					state.subSubscribed[subId] = true;
				}
				state.subSubscribed[subId] = !state.subSubscribed[subId];
				var isSubbed = state.subSubscribed[subId];
				MN.toast(
					isSubbed ? "Subscribed to channel" : "Unsubscribed from channel",
					"info",
				);
				render(el);
			};
		}

		var dlAllBtn = MN.qs('[data-act="download-all"]', el);
		if (dlAllBtn) {
			dlAllBtn.onclick = () => {
				var s = getSelectedSub();
				if (!s) return;
				var subVids = getSubVideos(s);
				openBulkQuality(subVids);
			};
		}

		var shortsBtn = MN.qs('[data-act="toggle-shorts"]', el);
		if (shortsBtn) {
			shortsBtn.onclick = () => {
				state.showShorts = !state.showShorts;
				setVisibleCount(10);
				render(el);
			};
		}

		// subscriptions
		el.querySelectorAll("[data-sub]").forEach((b) => {
			b.onclick = (e) => {
				if (e.target.closest("button")) return;
				var subId = parseInt(b.getAttribute("data-sub"), 10);
				var s = DATA.subscriptions.find((x) => x.id === subId);
				if (!s) return;
				state.selectedSubId = s.id;
				setVisibleCount(10);
				render(el);
			};
		});
		el.querySelectorAll("[data-autodl]").forEach((b) => {
			b.onclick = () => {
				var id = parseInt(b.getAttribute("data-autodl"), 10);
				var s = DATA.subscriptions.find((x) => x.id === id);
				if (!s) return;
				s.autoDownload = !s.autoDownload;
				MN.toast(
					s.autoDownload
						? "Auto-download enabled for " + s.name
						: "Auto-download disabled for " + s.name,
					"info",
				);
				render(el);
			};
		});
		el.querySelectorAll("[data-audioonly]").forEach((b) => {
			b.onclick = (e) => {
				e.stopPropagation();
				var id = parseInt(b.getAttribute("data-audioonly"), 10);
				var s = DATA.subscriptions.find((x) => x.id === id);
				if (!s) return;
				s.audioOnly = !s.audioOnly;
				MN.toast(
					s.audioOnly
						? "Audio-only auto-download enabled for " + s.name
						: "Audio-only auto-download disabled for " + s.name,
					"info",
				);
				render(el);
			};
		});
	}

	/* ---------------------------------------------------------------------------
     Interactions
     ------------------------------------------------------------------------- */
	function playFrom(v) {
		var list = visibleVideos().length ? visibleVideos() : [v];
		var idx = list.findIndex((x) => x.id === v.id);
		var queue = list.map((x) => ({
			id: x.id,
			title: x.title,
			channelName: x.channelName,
			durationSeconds: x.durationSeconds,
			thumbnailUrl: x.thumbnailUrl,
			resolution: x.resolution,
			isLocal: !!x.localFilePath,
		}));
		MN.playback.play(queue, idx >= 0 ? idx : 0);
		MN.router.navigate("player");
	}

	function toggleFavorite(id, el) {
		var v = findVideo(id);
		if (!v) return;
		v.favorite = !v.favorite;
		MN.toast(
			v.favorite ? "Added to favorites" : "Removed from favorites",
			"info",
		);
		render(el);
	}

	function toggleSelection(id, el) {
		if (state.selectedIds[id]) delete state.selectedIds[id];
		else state.selectedIds[id] = true;
		var count = Object.keys(state.selectedIds).length;
		if (count > 0) state.selectionMode = true;
		if (count === 0) state.selectionMode = false;
		render(el);
	}

	function openSortSheet() {
		var body = '<div class="mn-list">';
		SORTS.forEach((s) => {
			var active = s.id === state.sortCat;
			var dir = active ? state.sortDir : s.id === "name" ? "asc" : "desc";
			var iconName = dir === "asc" ? "arrow-up" : "arrow-down";
			body +=
				'<button class="mn-setting-row" data-sort-cat="' +
				s.id +
				'" title="' +
				MN.esc(s.label) +
				'">' +
				'<div class="mn-setting-row__icon">' +
				MN.icon(iconName, "mn-icon--sm") +
				"</div>" +
				'<div class="mn-setting-row__body"><div class="mn-setting-row__title">' +
				MN.esc(s.label) +
				"</div></div>" +
				(active ? MN.icon("check", "mn-icon--sm mn-accent") : "") +
				"</button>";
		});
		body += "</div>";
		MN.sheet({
			title: "Sort videos",
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-sort-cat]").forEach((b) => {
					b.onclick = () => {
						var cat = b.getAttribute("data-sort-cat");
						if (state.sortCat === cat) {
							state.sortDir = state.sortDir === "desc" ? "asc" : "desc";
						} else {
							state.sortCat = cat;
							state.sortDir = cat === "name" ? "asc" : "desc";
						}
						MN.closeSheet();
						var el = MN.qs("#view-root");
						render(el);
					};
				});
			},
		});
	}

	function openDownloadMenu(id) {
		var v = findVideo(id);
		if (!v) return;
		var formats = [];
		if (v.resolution === "Audio") {
			formats = [
				{
					format: "audio",
					label: "Audio · 128kbps (opus)",
					quality: "128kbps (opus)",
				},
			];
		} else {
			formats = [
				{
					format: "video",
					label: "Video · " + v.resolution + " (h264)",
					quality: v.resolution + " (h264)",
				},
				{ format: "video", label: "Video · 720p (vp9)", quality: "720p (vp9)" },
				{
					format: "audio",
					label: "Audio only · 128kbps (opus)",
					quality: "128kbps (opus)",
				},
			];
		}
		var body = '<div class="mn-list">';
		formats.forEach((f) => {
			body +=
				'<button class="mn-setting-row" data-qdl="' +
				f.format +
				"|" +
				f.quality +
				'" title="' +
				MN.esc(f.label) +
				'">' +
				'<div class="mn-setting-row__icon">' +
				MN.icon(f.format === "audio" ? "music" : "video") +
				"</div>" +
				'<div class="mn-setting-row__body"><div class="mn-setting-row__title">' +
				MN.esc(f.label) +
				"</div></div>" +
				MN.icon("download", "mn-icon--sm") +
				"</button>";
		});
		body += "</div>";
		MN.sheet({
			title: "Download format",
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-qdl]").forEach((b) => {
					b.onclick = () => {
						var parts = b.getAttribute("data-qdl").split("|");
						var format = parts[0];
						var quality = parts[1];
						MN.downloads.enqueue(v, format, quality);
						MN.closeSheet();
					};
				});
			},
		});
	}

	function sheetRow(iconName, title, dataKey, dataVal, danger) {
		return (
			'<button class="mn-setting-row" data-' +
			dataKey +
			'="' +
			dataVal +
			'" title="' +
			MN.esc(title) +
			'">' +
			'<div class="mn-setting-row__icon' +
			(danger ? '" style="color:var(--mn-destructive)' : "") +
			'">' +
			MN.icon(iconName, "mn-icon--sm") +
			"</div>" +
			'<div class="mn-setting-row__body"><div class="mn-setting-row__title' +
			(danger ? " mn-error" : "") +
			'">' +
			MN.esc(title) +
			"</div></div>" +
			"</button>"
		);
	}

	function openMoreSheet(id) {
		var v = findVideo(id);
		if (!v) return;
		var inFolder = state.tab === "folders" && currentFolderId() != null;
		var body = '<div class="mn-list">';
		body += sheetRow(
			v.favorite ? "heart" : "heart",
			v.favorite ? "Remove from favorites" : "Add to favorites",
			"fav",
			v.id,
			false,
		);
		body += sheetRow("download", "Download", "dl", v.id, false);
		body += sheetRow("move", "Move to folder", "moveto", v.id, false);
		body += sheetRow("eye", "Mark as watched", "watched", v.id, false);
		if (inFolder) {
			body += sheetRow("close", "Remove from folder", "remove", v.id, true);
		}
		body += "</div>";
		MN.sheet({
			title: MN.esc(v.title),
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-fav]").forEach((btn) => {
					btn.onclick = () => {
						MN.closeSheet();
						toggleFavorite(v.id, MN.qs("#view-root"));
					};
				});
				bodyEl.querySelectorAll("[data-dl]").forEach((btn) => {
					btn.onclick = () => {
						MN.closeSheet();
						openDownloadMenu(v.id);
					};
				});
				bodyEl.querySelectorAll("[data-moveto]").forEach((btn) => {
					btn.onclick = () => {
						openMoveDialog(v.id);
					};
				});
				bodyEl.querySelectorAll("[data-watched]").forEach((btn) => {
					btn.onclick = () => {
						v.watchCount = (v.watchCount || 0) + 1;
						MN.closeSheet();
						MN.toast("Marked as watched", "success");
						render(MN.qs("#view-root"));
					};
				});
				bodyEl.querySelectorAll("[data-remove]").forEach((btn) => {
					btn.onclick = () => {
						removeFromFolder(v.id);
					};
				});
			},
		});
	}

	function openFolderMoreSheet(id) {
		var f = DATA.folders.find((x) => x.id === id);
		if (!f) return;
		var body = '<div class="mn-list">';
		body += sheetRow("edit", "Rename folder", "rename", f.id, false);
		body += sheetRow("trash", "Delete folder", "delete", f.id, true);
		body += "</div>";
		MN.sheet({
			title: MN.esc(f.name),
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-rename]").forEach((btn) => {
					btn.onclick = () => {
						MN.closeSheet();
						openRenameDialog(parseInt(btn.getAttribute("data-rename"), 10));
					};
				});
				bodyEl.querySelectorAll("[data-delete]").forEach((btn) => {
					btn.onclick = () => {
						MN.closeSheet();
						openDeleteDialog(parseInt(btn.getAttribute("data-delete"), 10));
					};
				});
			},
		});
	}

	function openSubscriptionMoreSheet(id) {
		var s = DATA.subscriptions.find((x) => x.id === id);
		if (!s) return;
		var body = '<div class="mn-list">';
		body += sheetRow("close", "Unsubscribe", "unsub", s.id, true);
		body += "</div>";
		MN.sheet({
			title: MN.esc(s.name),
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-unsub]").forEach((btn) => {
					btn.onclick = () => {
						MN.closeSheet();
						openUnsubscribeDialog(parseInt(btn.getAttribute("data-unsub"), 10));
					};
				});
			},
		});
	}

	function removeFromFolder(id) {
		var folderId = currentFolderId();
		if (folderId == null) return;
		var arr = DATA.videoFolderMap[id] || [];
		DATA.videoFolderMap[id] = arr.filter((f) => f.id !== folderId);
		MN.closeSheet();
		MN.toast("Removed from folder", "info");
		var el = MN.qs("#view-root");
		render(el);
	}

	function openMoveDialog(id) {
		var folders = DATA.folders;
		if (!folders.length) {
			MN.closeSheet();
			MN.dialog({
				title: "No folders",
				body: "Create a folder first, then move videos into it.",
				actions: [{ label: "OK", cls: "mn-btn--primary" }],
			});
			return;
		}
		var body = '<div class="mn-list">';
		folders.forEach((f) => {
			body +=
				'<button class="mn-setting-row" data-pickfolder="' +
				f.id +
				'" title="' +
				MN.esc(f.name) +
				'">' +
				'<div class="mn-setting-row__icon">' +
				MN.icon("folder") +
				"</div>" +
				'<div class="mn-setting-row__body"><div class="mn-setting-row__title">' +
				MN.esc(f.name) +
				"</div></div>" +
				"</button>";
		});
		body += "</div>";
		MN.closeSheet();
		MN.sheet({
			title: "Move to folder",
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-pickfolder]").forEach((b) => {
					b.onclick = () => {
						var fid = parseInt(b.getAttribute("data-pickfolder"), 10);
						var folder = DATA.folders.find((x) => x.id === fid);
						if (!folder) return;
						var arr = DATA.videoFolderMap[id] || [];
						if (!arr.some((x) => x.id === fid)) {
							DATA.videoFolderMap[id] = arr.concat([folder]);
						}
						MN.closeSheet();
						MN.toast("Moved to " + folder.name, "info");
						var el = MN.qs("#view-root");
						render(el);
					};
				});
			},
		});
	}

	function openNewFolderDialog() {
		var parentId = currentFolderId();
		var body =
			'<div class="mn-field">' +
			'<input id="newfolder-name" type="text" placeholder="Folder name" />' +
			"</div>";
		MN.dialog({
			title: "New folder",
			body: body,
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost" },
				{
					label: "Create",
					cls: "mn-btn--primary",
					onClick: () => {
						var input = MN.qs("#newfolder-name");
						var name = input ? input.value.trim() : "";
						if (!name) {
							MN.toast("Enter a folder name", "error");
							return;
						}
						DATA.folders.push({
							id: Date.now(),
							name: name,
							parentId: parentId,
							createdAt: Date.now(),
						});
						MN.toast("Folder created", "success");
						var el = MN.qs("#view-root");
						render(el);
					},
				},
			],
		});
		setTimeout(() => {
			var input = MN.qs("#newfolder-name");
			if (input) input.focus();
		}, 50);
	}

	function openRenameDialog(id) {
		var f = DATA.folders.find((x) => x.id === id);
		if (!f) return;
		var body =
			'<div class="mn-field">' +
			'<input id="rename-name" type="text" value="' +
			MN.esc(f.name) +
			'" />' +
			"</div>";
		MN.dialog({
			title: "Rename folder",
			body: body,
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost" },
				{
					label: "Rename",
					cls: "mn-btn--primary",
					onClick: () => {
						var input = MN.qs("#rename-name");
						var name = input ? input.value.trim() : "";
						if (!name) return;
						f.name = name;
						MN.toast("Folder renamed", "success");
						var el = MN.qs("#view-root");
						render(el);
					},
				},
			],
		});
	}

	function openDeleteDialog(id) {
		var f = DATA.folders.find((x) => x.id === id);
		if (!f) return;
		var body =
			"<p>Delete folder “" +
			MN.esc(f.name) +
			"”?</p>" +
			'<label class="mn-row mn-gap-2" style="margin-top:12px">' +
			'<span class="mn-checkbox" id="del-videos-chk">' +
			MN.icon("check") +
			"</span>" +
			'<span class="mn-muted">Also delete downloaded videos in this folder</span>' +
			"</label>";
		MN.dialog({
			title: "Delete folder",
			body: body,
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost" },
				{
					label: "Delete",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						var deleteVideos = false;
						var chk = MN.qs("#del-videos-chk");
						if (chk) deleteVideos = chk.classList.contains("mn-checkbox--checked");
						var idsToRemove = [f.id];
						var children = DATA.folders.filter((x) => x.parentId === f.id);
						children.forEach((c) => {
							idsToRemove.push(c.id);
						});
						DATA.folders = DATA.folders.filter((x) => !idsToRemove.includes(x.id));
						Object.keys(DATA.videoFolderMap).forEach((vid) => {
							DATA.videoFolderMap[vid] = DATA.videoFolderMap[vid].filter(
								(x) => !idsToRemove.includes(x.id),
							);
						});
						if (deleteVideos) {
							DATA.videos.forEach((v) => {
								var still = DATA.videoFolderMap[v.id] || [];
								if (!still.length && v.localFilePath) v.localFilePath = "";
							});
						}
						if (state.folderStack.includes(f.id)) state.folderStack = [];
						MN.toast("Folder deleted", "success");
						var el = MN.qs("#view-root");
						render(el);
					},
				},
			],
		});
		setTimeout(() => {
			var chk = MN.qs("#del-videos-chk");
			if (chk)
				chk.onclick = () => {
					chk.classList.toggle("mn-checkbox--checked");
				};
		}, 50);
	}

	function openUnsubscribeDialog(id) {
		var s = DATA.subscriptions.find((x) => x.id === id);
		if (!s) return;
		MN.dialog({
			title: s.sourceType === "playlist" ? "Remove playlist" : "Unsubscribe",
			body:
				"Stop following “" + MN.esc(s.name) + "”? Auto-download will also stop.",
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost" },
				{
					label: s.sourceType === "playlist" ? "Remove" : "Unsubscribe",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						DATA.subscriptions = DATA.subscriptions.filter((x) => x.id !== s.id);
						if (state.selectedSubId === s.id) state.selectedSubId = null;
						MN.toast("Unsubscribed from " + s.name, "info");
						var el = MN.qs("#view-root");
						render(el);
					},
				},
			],
		});
	}

	function openBulkQuality(rawList) {
		var list = state.showShorts ? rawList : rawList.filter((v) => !v.isShort);
		var qualities = ["1080p", "720p", "480p", "360p", "Audio"];
		var html = '<div class="mn-list">';
		qualities.forEach((q) => {
			html +=
				'<button class="mn-setting-row" style="width:100%" data-q="' +
				q +
				'" title="Download all at ' +
				MN.esc(q) +
				'">' +
				'<span class="mn-setting-row__icon">' +
				MN.icon(q === "Audio" ? "music" : "video") +
				"</span>" +
				'<span class="mn-setting-row__body"><span class="mn-setting-row__title">' +
				MN.esc(q) +
				"</span></span>" +
				MN.icon("chevron-right") +
				"</button>";
		});
		html += "</div>";
		MN.sheet({
			title: "Download All by Resolution",
			body: html,
			onOpen: (body) => {
				body.querySelectorAll("[data-q]").forEach((btn) => {
					btn.onclick = () => {
						var q = btn.getAttribute("data-q");
						MN.closeSheet();
						runBulk(q, list);
					};
				});
			},
		});
	}

	function runBulk(quality, list) {
		var total = list.length;
		var cur = 0;
		MN.dialog({
			title: "Fetching Video Metadata",
			body: progressBody(0, total),
			dismissible: false,
		});
		var timer = setInterval(() => {
			cur++;
			var body = MN.qs("#dialog .mn-dialog__body");
			if (body) MN.render(body, progressBody(cur, total));
			if (cur >= total) {
				clearInterval(timer);
				MN.closeDialog();
				showBulkConfirm(quality, list);
			}
		}, 220);
	}

	function progressBody(cur, total) {
		var pct = total ? Math.round((cur / total) * 100) : 0;
		return (
			'<p class="mn-muted">Retrieving stream details to calculate size and check disk space.</p>' +
			'<p style="margin:10px 0 6px">Progress: ' +
			cur +
			" of " +
			total +
			" videos</p>" +
			'<div class="mn-progress"><span style="width:' +
			pct +
			'%"></span></div>'
		);
	}

	function estSize(sec, quality) {
		var bitrate = 2500000;
		if (quality === "1080p") bitrate = 4500000;
		else if (quality === "720p") bitrate = 2500000;
		else if (quality === "480p") bitrate = 1200000;
		else if (quality === "360p") bitrate = 800000;
		else if (quality === "Audio") bitrate = 128000;
		return Math.round((sec * bitrate) / 8);
	}

	function showBulkConfirm(quality, list) {
		var totalSize = 0;
		list.forEach((pv) => {
			totalSize += estSize(pv.durationSeconds || 180, quality);
		});
		MN.dialog({
			title: "Confirm Bulk Download",
			body:
				"<p>Quality: <strong>" +
				MN.esc(quality) +
				"</strong></p>" +
				"<p>Total Videos: <strong>" +
				list.length +
				"</strong></p>" +
				"<p>Total Download Size: <strong>" +
				MN.fmt.bytes(totalSize) +
				"</strong></p>" +
				'<p class="mn-success" style="margin-top:10px">' +
				MN.icon("check-circle", "mn-icon--sm") +
				" Storage check: sufficient space available.</p>",
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost" },
				{
					label: "Download",
					cls: "mn-btn--primary",
					onClick: () => {
						var isAudio = quality === "Audio";
						list.forEach((pv) => {
							var v = enrichSubVideo(pv);
							MN.downloads.enqueue(
								v,
								isAudio ? "audio" : "video",
								isAudio ? "128kbps (opus)" : quality,
							);
						});
						MN.toast("Bulk download started", "success");
					},
				},
			],
		});
	}

	/* ---------------------------------------------------------------------------
     Register route
     ------------------------------------------------------------------------- */
	MN.router.register("collections", {
		title: "Collections",
		back: false,
		actions: () => {
			var iconName = state.view === "grid" ? "list" : "grid";
			var label = state.view === "grid" ? "List view" : "Grid view";
			return (
				'<button class="mn-icon-btn" id="view-toggle" aria-label="' +
				label +
				'" title="' +
				label +
				'">' +
				MN.icon(iconName) +
				"</button>" +
				'<button class="mn-icon-btn" id="stats-toggle" aria-label="App Statistics" title="App Statistics">' +
				MN.icon("chart") +
				"</button>"
			);
		},
		mount: mount,
	});
})();
