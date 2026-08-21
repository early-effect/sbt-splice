function Component() {
  this.props = {};
  this.state = {};
  this.context = {};
}
Component.prototype.setState = function (update) {
  this.state = update;
  this._recorded = (this._recorded || "") + "STATE:" + JSON.stringify(update);
};
Component.prototype.forceUpdate = function () {
  this._recorded = (this._recorded || "") + "FORCE";
};
Component.prototype.render = function () {
  return "BASE_RENDER";
};
Component.prototype.componentWillMount = function () {};
Component.prototype.componentDidMount = function () {
  return "BASE_DID_MOUNT";
};
Component.prototype.componentWillUnmount = function () {};
Component.prototype.componentDidUpdate = function () {};
Component.prototype.shouldComponentUpdate = function () {
  return true;
};
function unused() {
  return "DEAD_CODE_MARKER";
}
function mount(type) {
  var h = new type();
  if (h.componentWillMount) h.componentWillMount();
  var rendered = h.render();
  var did = h.componentDidMount ? h.componentDidMount() : "";
  return (h._recorded || "") + "|" + rendered + "|" + did;
}
function h(tag) {
  return tag;
}
export { Component, mount, h, unused };
