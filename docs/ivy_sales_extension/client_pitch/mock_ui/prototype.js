const tabs = document.querySelectorAll('.tab');
const screens = document.querySelectorAll('.screen');

tabs.forEach(tab => {
  tab.addEventListener('click', () => {
    tabs.forEach(t => t.classList.remove('active'));
    screens.forEach(s => s.classList.remove('active'));
    tab.classList.add('active');
    const target = document.getElementById(tab.dataset.screen);
    if (target) target.classList.add('active');
  });
});
