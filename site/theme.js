/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(() => {
  'use strict';
  const root = document.documentElement;
  const button = document.getElementById('theme-toggle');
  let theme = 'light';
  try { if (localStorage.getItem('warehouse-theme') === 'dark') theme = 'dark'; } catch {}
  function render() {
    root.dataset.theme = theme;
    const chinese = root.lang.startsWith('zh');
    button.textContent = theme === 'light'
      ? (chinese ? '夜间模式' : 'Dark mode')
      : (chinese ? '白天模式' : 'Light mode');
    button.setAttribute('aria-label', button.textContent);
    document.querySelector('.brand-logo').src = `assets/logo.svg${theme === 'dark' ? '#night' : ''}`;
    document.querySelector('link[rel="icon"]').href = `assets/logo.svg${theme === 'dark' ? '#night' : ''}`;
    button.hidden = false;
  }
  button.addEventListener('click', () => {
    theme = theme === 'light' ? 'dark' : 'light';
    try { localStorage.setItem('warehouse-theme', theme); } catch {}
    render();
  });
  document.addEventListener('warehouse-language-change', render);
  render();
})();
