function HtmlElement() {}
HtmlElement.prototype.connectedCallback = function () {
  return "BASE_CONNECTED";
};
HtmlElement.prototype.disconnectedCallback = function () {
  return "BASE_DISCONNECTED";
};
HtmlElement.prototype.attributeChangedCallback = function () {
  return "BASE_ATTR";
};
function upgrade(type) {
  return new type().connectedCallback();
}
export { HtmlElement, upgrade };
