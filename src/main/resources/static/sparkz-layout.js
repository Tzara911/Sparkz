(() => {
  const links = [
    { page: "discover", label: "Discover", href: "/" },
    { page: "matches", label: "Match requests", href: "/matches.html" },
    { page: "profile", label: "My profile", href: "/profile.html" }
  ];

  const linkMarkup = (currentPage) => links.map(({ page, label, href }) =>
    `<a class="sparkz-nav-link${page === currentPage ? " is-current" : ""}" href="${href}"${page === currentPage ? ' aria-current="page"' : ""}>${label}</a>`
  ).join("");

  document.querySelectorAll("[data-sparkz-header]").forEach((mount) => {
    const page = document.body.dataset.page || "";
    mount.innerHTML = `<header class="sparkz-header"><div class="container sparkz-header-inner">
      <a class="sparkz-brand" href="/" aria-label="Sparkz home">Sparkz<span aria-hidden="true">✦</span></a>
      <button class="sparkz-menu-toggle" type="button" aria-expanded="false" aria-controls="sparkz-primary-navigation"><span class="visually-hidden">Toggle navigation</span><span aria-hidden="true">☰</span></button>
      <nav class="sparkz-navigation" id="sparkz-primary-navigation" aria-label="Main navigation">${linkMarkup(page)}</nav>
      <div class="sparkz-help-wrap">
        <button class="sparkz-help-toggle" type="button" aria-label="Help and more pages" aria-expanded="false" aria-controls="sparkz-help-menu">?</button>
        <div class="sparkz-help-menu" id="sparkz-help-menu" hidden>
          <p class="sparkz-help-title">Quick links</p>
          <a href="/login.html">Login</a>
          <a href="/onboarding.html">Onboarding</a>
          <a href="/availability.html">Manage Availability</a>
        </div>
      </div>
    </div></header>`;
    const toggle = mount.querySelector(".sparkz-menu-toggle");
    const navigation = mount.querySelector(".sparkz-navigation");
    toggle.addEventListener("click", () => {
      const expanded = toggle.getAttribute("aria-expanded") === "true";
      toggle.setAttribute("aria-expanded", String(!expanded));
      navigation.classList.toggle("is-open", !expanded);
    });
    const helpToggle = mount.querySelector(".sparkz-help-toggle");
    const helpMenu = mount.querySelector(".sparkz-help-menu");
    helpToggle.addEventListener("click", () => {
      const expanded = helpToggle.getAttribute("aria-expanded") === "true";
      helpToggle.setAttribute("aria-expanded", String(!expanded));
      helpMenu.hidden = expanded;
    });
    mount.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !helpMenu.hidden) {
        helpToggle.setAttribute("aria-expanded", "false");
        helpMenu.hidden = true;
        helpToggle.focus();
      }
    });
  });

  document.querySelectorAll("[data-sparkz-footer]").forEach((mount) => {
    mount.innerHTML = `<footer class="sparkz-footer"><div class="sparkz-footer-inner">
      <a class="sparkz-footer-brand" href="/" aria-label="Sparkz home">Sparkz<span aria-hidden="true">✦</span></a>
      <span class="sparkz-footer-divider" aria-hidden="true"></span>
      <p>Good things happen when we share.</p>
      <span class="sparkz-footer-star" aria-hidden="true">✦</span>
    </div></footer>`;
  });
})();
