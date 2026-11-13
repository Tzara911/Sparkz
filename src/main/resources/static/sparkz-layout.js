(() => {
  const links = [
    { page: "home", label: "Home", href: "/" },
    { page: "login", label: "Login", available: false },
    { page: "onboarding", label: "Onboarding", available: false },
    { page: "profile", label: "Profile", available: false },
    { page: "browse", label: "Browse", href: "/browse.html" },
    { page: "availability", label: "Manage Availability", available: false },
    { page: "matches", label: "Match requests", href: "/matches.html" }
  ];

  const linkMarkup = (currentPage) => links.map(({ page, label, href, available }) =>
    available === false
      ? `<span class="sparkz-nav-link" aria-disabled="true" title="This page is not available yet">${label}</span>`
      : `<a class="sparkz-nav-link${page === currentPage ? " is-current" : ""}" href="${href}"${page === currentPage ? ' aria-current="page"' : ""}>${label}</a>`
  ).join("");

  document.querySelectorAll("[data-sparkz-header]").forEach((mount) => {
    const page = document.body.dataset.page || "";
    mount.innerHTML = `<header class="sparkz-header"><div class="container sparkz-header-inner">
      <a class="sparkz-brand" href="/" aria-label="Sparkz home"><img src="/images/sparkz-logo.png" alt="Sparkz" width="184" height="64"></a>
      <button class="sparkz-menu-toggle" type="button" aria-expanded="false" aria-controls="sparkz-primary-navigation"><span class="visually-hidden">Toggle navigation</span><span aria-hidden="true">☰</span></button>
      <nav class="sparkz-navigation" id="sparkz-primary-navigation" aria-label="Main navigation">${linkMarkup(page)}</nav>
    </div></header>`;
    const toggle = mount.querySelector(".sparkz-menu-toggle");
    const navigation = mount.querySelector(".sparkz-navigation");
    toggle.addEventListener("click", () => {
      const expanded = toggle.getAttribute("aria-expanded") === "true";
      toggle.setAttribute("aria-expanded", String(!expanded));
      navigation.classList.toggle("is-open", !expanded);
    });
    mount.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
        toggle.setAttribute("aria-expanded", "false");
        navigation.classList.remove("is-open");
        toggle.focus();
      }
    });
  });

  document.querySelectorAll("[data-sparkz-footer]").forEach((mount) => {
    mount.innerHTML = `<footer class="sparkz-footer"><div class="container d-flex flex-column flex-sm-row justify-content-between gap-2"><span>© ${new Date().getFullYear()} Sparkz</span><span>Learn something. Teach something.</span></div></footer>`;
  });
})();
