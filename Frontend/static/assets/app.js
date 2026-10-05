'use strict';
document.querySelectorAll('[data-toggle]').forEach(button => {
  button.addEventListener('click', () => {
    const target = document.getElementById(button.dataset.toggle);
    target.hidden = !target.hidden;
    button.setAttribute('aria-expanded', String(!target.hidden));
  });
});
document.getElementById('go-back')?.addEventListener('click', () => history.back());
const clp = n => new Intl.NumberFormat('es-CL', {style:'currency', currency:'CLP', maximumFractionDigits:0}).format(n);
const saleForm = document.getElementById('sale-form');
if (saleForm) {
  const rows = document.getElementById('sale-rows');
  let nextRow = 2;
  const location = saleForm.querySelector('[name=locationId]');
  const realLocations = [...location.options].filter(option => option.value);
  if (!location.value && realLocations.length === 1) location.value = realLocations[0].value;
  function update() {
    let total = 0n;
    const grouped = new Map();
    rows.querySelectorAll('.sale-row').forEach(row => {
      const selected = row.querySelector('select').selectedOptions[0];
      const input = row.querySelector('.sale-quantity');
      input.step = selected?.dataset.unit === 'unidad' ? '1' : '0.001';
      input.min = input.step;
      input.previousElementSibling.textContent = selected?.dataset.unit === 'unidad' ? 'Cantidad (unidades)' : selected?.dataset.unit === 'kg' ? 'Cantidad (kilos)' : 'Cantidad';
      const hint = row.querySelector('.quantity-hint');
      if (hint) hint.textContent = selected?.dataset.unit === 'kg' ? '250 gramos = 0,250 kilos' : '';
      const price = BigInt(selected?.dataset.price || 0);
      const qty = BigInt(Math.max(0, Math.round(Number(input.value || 0) * 1000)));
      const subtotal = (price * qty + 500n) / 1000n;
      row.querySelector('.sale-subtotal').textContent = clp(subtotal);
      const key = selected?.value || '';
      const group = grouped.get(key) || {price, qty:0n};
      group.qty += qty;
      grouped.set(key, group);
      row.querySelector('.remove-row').disabled = rows.children.length === 1;
    });
    grouped.forEach(group => { total += (group.price * group.qty + 500n) / 1000n; });
    document.getElementById('sale-total').textContent = clp(total);
  }
  function bind(row) {
    row.querySelector('select').addEventListener('change', update);
    row.querySelector('.sale-quantity').addEventListener('input', update);
    row.querySelector('.remove-row').addEventListener('click', () => { if (rows.children.length > 1) row.remove(); update(); });
  }
  bind(rows.firstElementChild);
  document.getElementById('add-sale-row').addEventListener('click', () => {
    if (rows.children.length >= 50) return;
    const row = rows.firstElementChild.cloneNode(true);
    row.querySelector('select').value = '';
    row.querySelector('select').id = `sale-product-${nextRow}`;
    row.querySelector('.sale-product label').htmlFor = `sale-product-${nextRow}`;
    row.querySelector('.sale-quantity').id = `sale-quantity-${nextRow}`;
    row.querySelector('.sale-quantity').previousElementSibling.htmlFor = `sale-quantity-${nextRow}`;
    row.querySelector('.sale-quantity').value = '1';
    nextRow += 1;
    rows.append(row); bind(row); update();
  });
  saleForm.addEventListener('submit', () => { saleForm.querySelector('[type=submit]').disabled = true; });
  update();
}
const receiptProduct = document.getElementById('productId');
if (receiptProduct) {
  const amount = document.getElementById('quantity');
  const updateUnit = () => { amount.step = receiptProduct.selectedOptions[0]?.dataset.unit === 'unidad' ? '1' : '0.001'; amount.min = amount.step; };
  receiptProduct.addEventListener('change', updateUnit); updateUnit();
}
