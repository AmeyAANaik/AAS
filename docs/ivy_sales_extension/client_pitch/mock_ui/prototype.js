const personaTabs = document.querySelectorAll('.tab');
const personas = document.querySelectorAll('.persona');

personaTabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    personaTabs.forEach((t) => t.classList.remove('active'));
    personas.forEach((p) => p.classList.remove('active'));
    tab.classList.add('active');
    document.getElementById(tab.dataset.persona)?.classList.add('active');
  });
});

const menuItems = document.querySelectorAll('.menu-item');
menuItems.forEach((item) => {
  item.addEventListener('click', () => {
    menuItems.forEach((m) => m.classList.remove('active'));
    item.classList.add('active');
  });
});
