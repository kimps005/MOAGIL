(() => {
  const story = document.querySelector("[data-scroll-story]");
  if (!story) return;

  const sticky = story.querySelector("[data-story-sticky]");
  const scenes = [...story.querySelectorAll("[data-story-scene]")];
  const progressItems = [...story.querySelectorAll(".story-progress li")];
  if (!sticky || scenes.length < 2) return;

  document.documentElement.classList.add("has-scroll-story");

  let activeIndex = -1;
  let frame = 0;

  function setActive(index) {
    if (index === activeIndex) return;
    activeIndex = index;

    scenes.forEach((scene, sceneIndex) => {
      const active = sceneIndex === index;
      scene.classList.toggle("is-active", active);
      scene.setAttribute("aria-hidden", String(!active));
      scene.inert = !active;
    });

    progressItems.forEach((item, itemIndex) => {
      if (itemIndex === index) item.setAttribute("aria-current", "step");
      else item.removeAttribute("aria-current");
    });

    sticky.classList.toggle("is-final", index === scenes.length - 1);
  }

  function updateScene() {
    frame = 0;
    const distance = Math.max(1, story.offsetHeight - sticky.offsetHeight);
    const storyTop = story.getBoundingClientRect().top + window.scrollY;
    const progress = Math.min(0.999999, Math.max(0, (window.scrollY - storyTop) / distance));
    setActive(Math.min(scenes.length - 1, Math.floor(progress * scenes.length)));
  }

  function scheduleUpdate() {
    if (frame) return;
    frame = window.requestAnimationFrame(updateScene);
  }

  window.addEventListener("scroll", scheduleUpdate, { passive: true });
  window.addEventListener("resize", scheduleUpdate, { passive: true });
  updateScene();
  window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
    document.documentElement.classList.add("story-ready");
  }));
})();
