function Component() {}
Component.prototype.render = function () {
  return "BASE_RENDER";
};
function mount(type) {
  var h = new type();
  return h.render();
}
export { Component, mount };
