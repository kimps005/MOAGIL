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
  let inputLocked = false;
  let inputLockTimer = 0;
  let touchStart = null;

  function isShortViewport() {
    return window.matchMedia("(max-height: 620px)").matches;
  }

  function showAllScenes() {
    activeIndex = -1;
    scenes.forEach(scene => {
      scene.classList.remove("is-active");
      scene.setAttribute("aria-hidden", "false");
      scene.inert = false;
    });
    progressItems.forEach(item => item.removeAttribute("aria-current"));
    sticky.classList.remove("is-final");
  }

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
    if (isShortViewport()) {
      showAllScenes();
      return;
    }
    const distance = Math.max(1, story.offsetHeight - sticky.offsetHeight);
    const storyTop = story.getBoundingClientRect().top + window.scrollY;
    const progress = Math.min(0.999999, Math.max(0, (window.scrollY - storyTop) / distance));
    setActive(Math.min(scenes.length - 1, Math.floor(progress * scenes.length)));
  }

  function scheduleUpdate() {
    if (frame) return;
    frame = window.requestAnimationFrame(updateScene);
  }

  function getStoryRange() {
    const storyTop = story.getBoundingClientRect().top + window.scrollY;
    const travel = Math.max(1, story.offsetHeight - sticky.offsetHeight);
    const headerHeight = Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--header-height")) || 0;
    return { storyTop, travel, start: storyTop - headerHeight - 2, end: storyTop + travel + 2 };
  }

  function isInStoryRange() {
    const range = getStoryRange();
    return window.scrollY >= range.start && window.scrollY <= range.end;
  }

  function scrollToScene(index) {
    const range = getStoryRange();
    const sceneProgress = (index + 0.5) / scenes.length;
    const target = range.storyTop + range.travel * sceneProgress;
    const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
    window.scrollTo({ top: target, behavior });
  }

  function lockInput() {
    inputLocked = true;
    window.clearTimeout(inputLockTimer);
    inputLockTimer = window.setTimeout(() => {
      inputLocked = false;
    }, 480);
  }

  function handleWheel(event) {
    if (isShortViewport() || event.ctrlKey || Math.abs(event.deltaY) < 20 || !isInStoryRange()) return;

    const direction = event.deltaY > 0 ? 1 : -1;
    const nextIndex = activeIndex + direction;
    if (nextIndex < 0 || nextIndex >= scenes.length) return;

    event.preventDefault();
    if (inputLocked) {
      lockInput();
      return;
    }

    scrollToScene(nextIndex);
    lockInput();
  }

  function canMoveBySwipe(deltaY) {
    if (isShortViewport()) return false;
    const direction = deltaY > 0 ? 1 : -1;
    const nextIndex = activeIndex + direction;
    return isInStoryRange() && nextIndex >= 0 && nextIndex < scenes.length;
  }

  function handleTouchStart(event) {
    if (event.touches.length !== 1) {
      touchStart = null;
      return;
    }
    const touch = event.touches[0];
    touchStart = { x: touch.clientX, y: touch.clientY };
  }

  function handleTouchMove(event) {
    if (!touchStart || event.touches.length !== 1) return;
    const touch = event.touches[0];
    const deltaY = touchStart.y - touch.clientY;
    const deltaX = touchStart.x - touch.clientX;
    if (Math.abs(deltaY) < 12 || Math.abs(deltaY) <= Math.abs(deltaX) || !canMoveBySwipe(deltaY)) return;
    event.preventDefault();
  }

  function handleTouchEnd(event) {
    if (!touchStart || !event.changedTouches.length) return;
    const touch = event.changedTouches[0];
    const deltaY = touchStart.y - touch.clientY;
    const deltaX = touchStart.x - touch.clientX;
    touchStart = null;
    if (Math.abs(deltaY) < 44 || Math.abs(deltaY) <= Math.abs(deltaX) || !canMoveBySwipe(deltaY)) return;

    if (inputLocked) {
      lockInput();
      return;
    }

    scrollToScene(activeIndex + (deltaY > 0 ? 1 : -1));
    lockInput();
  }

  window.addEventListener("scroll", scheduleUpdate, { passive: true });
  window.addEventListener("resize", scheduleUpdate, { passive: true });
  window.addEventListener("wheel", handleWheel, { passive: false });
  story.addEventListener("touchstart", handleTouchStart, { passive: true });
  story.addEventListener("touchmove", handleTouchMove, { passive: false });
  story.addEventListener("touchend", handleTouchEnd, { passive: true });
  updateScene();
  window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
    document.documentElement.classList.add("story-ready");
  }));
})();
