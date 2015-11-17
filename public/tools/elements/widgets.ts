import { Element, Property, Listener, Dependencies, PolymerElement } from "elements/polymer";

@Element("gt-form")
export class GtForm extends PolymerElement {
	/**
	 * Indicate an error during form validation
	 */
	@Property
	public failed: boolean;

	/**
	 * Trigger the submit event
	 */
	public submit() {
		for (let input of this.node.querySelectorAll("gt-input[required]").map(n => Polymer.cast(n, GtInput))) {
			if (input.value.match(/^\s*$/)) {
				input.error = "This field is required";
				input.focus();
				this.failed = true;
				return;
			} else {
				input.error = null;
			}
		}
		this.failed = false;
		this.fire("submit");
	}
}

/**
 * Text input
 */
@Element("gt-input", "/assets/imports/widgets.html")
@Dependencies(GtForm)
export class GtInput extends PolymerElement {
	/**
	 * Disable the input
	 */
	@Property({ reflect: true })
	public disabled: boolean;

	/**
	 * Prevent form submission if empty
	 */
	@Property({ reflect: true })
	public required: boolean;

	/**
	 * Input type "text", "password"
	 */
	@Property
	public type: string = "text";

	/**
	 * Error message
	 */
	@Property({ reflect: true, notify: true })
	public error: string = null;

	/**
	 * Proxy to input value
	 */
	@Property({ notify: true })
	public value: string = "";

	/**
	 * Focus the input
	 */
	public focus() {
		this.$.input.focus();
	}

	/**
	 * Blur the input
	 */
	public blur() {
		this.$.input.blur();
	}

	/**
	 * Catch Enter-key presses in the input and forward to the form
	 */
	@Listener("input.keypress")
	private InputKeypress(e: KeyboardEvent) {
		if (e.keyCode == 13) {
			e.preventDefault();
			const form = this.host(GtForm);
			if (form) {
				this.blur();
				form.submit();
			}
		}
	}

	@Listener("input.keyup")
	private InputUp(e: KeyboardEvent) {
		this.debounce("value-changed", () => this.value = this.$.input.value, 200);
	}

	public click() {
		this.focus();
	}

	/**
	 * Reflect input focused state to the outer <label> element
	 */
	@Listener("input.focus") private InputFocus() {
		this.toggleAttribute("focused", true);
	}

	@Listener("input.blur") private InputBlur() {
		this.toggleAttribute("focused", false);
	}
}

/**
 * <gt-checkbox [checked]="foobar">
 * <gt-checkbox [radio]="foobar" value="foobar">
 */
@Element("gt-checkbox", "/assets/imports/widgets.html")
export class GtCheckbox extends PolymerElement {
	@Property({ reflect: true })
	public disabled: boolean;

	@Property({ reflect: true, notify: true, observer: "RadioChanged" })
	public radio: string;

	@Property({ reflect: true })
	public value: string;

	@Property({ reflect: true, notify: true })
	public checked: boolean;

	/**
	 * Click generator
	 */
	public click() {
		this.fire("click");
	}

	/**
	 * Click listener
	 */
	@Listener("click", "tap")
	private ClickEvent(e: Event) {
		if (this.disabled) return this.stopEvent(e);
		else if (this.value) this.radio = this.value;
		else this.checked = !this.checked;
	}

	private RadioChanged() {
		this.checked = this.radio == this.value;
	}
}

/**
 * <gt-label>
 */
@Element("gt-label", "/assets/imports/widgets.html")
export class GtLabel extends PolymerElement {
	@Listener("click")
	private click(ev: Event) {
		let control: any = this.node.querySelector("gt-input, gt-checkbox");
		if (control && control != ev.target) control.click();
	}

	@Listener("mouseenter")
	private enter(ev: Event) {
		let control: any = this.node.querySelector("gt-input, gt-checkbox");
		if (control) control.setAttribute("hover", true);
	}

	@Listener("mouseleave")
	private leaver(ev: Event) {
		let control: any = this.node.querySelector("gt-input, gt-checkbox");
		if (control) control.removeAttribute("hover");
	}
}

/**
 * <gt-textarea>
 */
@Element("gt-textarea", "/assets/imports/widgets.html")
export class GtTextarea extends PolymerElement {
	@Property({ notify: true, observer: "ValueChanged" })
	public value: string;

	@Property public disabled: boolean;

	@Listener("textarea.keyup")
	private InputUp(e: KeyboardEvent) {
		this.debounce("value-changed", () => this.value = this.$.textarea.value, 200);
	}

	@Listener("textarea.change")
	private Changed() {
		this.value = this.$.textarea.value;
	}

	private ValueChanged() {
		if (this.$.textarea.value != this.value) {
			this.$.textarea.value = this.value;
		}
	}
}

/**
 * Clickable button
 */
@Element("gt-button", "/assets/imports/widgets.html")
@Dependencies(GtForm)
export class GtButton extends PolymerElement {
	/**
	 * Disable the button, prevent event triggering
	 */
	@Property({ reflect: true })
	public disabled: boolean;

	/**
	 * If set, clicking the button will trigger the submit event in the enclosing GtForm
	 */
	@Property public submit: boolean;

	/**
	 * If set, clicking the button will trigger navigation
	 */
	@Property public goto: string;

	/**
	 * Click handler
	 */
	@Listener("click", "tap")
	private ClickEvent(e: Event) {
		// Button is disabled
		if (this.disabled) return this.stopEvent(e);

		// Button is a link
		if (this.goto) {
			this.app.router.goto(this.goto);
			return;
		}

		// Button is a form submit action
		if (this.submit) {
			const form = this.host(GtForm);
			if (form) form.submit();
		}
	}
}

/**
 * <gt-progress-circular>
 */
@Element("gt-progress-circular", "/assets/imports/widgets.html")
export class GtProgressCircular extends PolymerElement {
	/**
	 * Length of a full radius circle
	 */
	private static fullRadius = 226;

	/**
	 * Progress bar color
	 */
	@Property public color: string = "#64B4FF";

	/**
	 * Background color
	 */
	@Property public back: string = "rgba(17, 17, 17, 0.2)";

	/**
	 * Rotation of the progress indicator.
	 * Each unit is 90 deg.
	 */
	@Property public rotate: number = 0;

	/**
	 * Width of the gap in degree.
	 */
	@Property public gap: number = 0;

	/**
	 * Min value
	 */
	@Property public min: number = 0;

	/**
	 * Max value
	 */
	@Property public max: number = 1;

	/**
	 * Current value
	 */
	@Property public progress: number = 0;

	/**
	 * Reverse the filling direction of the indicator
	 */
	@Property({ observer: "ReverseUpdated" })
	public reverse: boolean = false;

	/**
	 * The background circle
	 */
	private back_circle: SVGCircleElement;

	/**
	 * The progress bar circle
	 */
	private bar_circle: SVGCircleElement;

	/**
	 * Initialize the component by binding SVG circles to local fields
	 */
	private init() {
		this.back_circle = this.$.back;
		this.bar_circle = this.$.bar;
	}

	/**
	 * Handle gap computation
	 */
	@Property({ computed: "gap", observer: "UpdateGap" })
	private get gapOffset(): number {
		return this.gap / 360 * GtProgressCircular.fullRadius;
	}

	private UpdateGap() {
		this.back_circle.style.strokeDashoffset = String(this.gapOffset);
	}

	/**
	 * Handle offset of the progress bar
	 */
	@Property({ computed: "min max progress gapOffset", observer: "UpdateOffset" })
	private get offsetValue(): number {
		let value = Math.max(this.min, Math.min(this.max, this.progress));
		let percent = (value - this.min) / (this.max - this.min);
		let gap = this.gapOffset;
		return (1 - percent) * (GtProgressCircular.fullRadius - gap) + gap;
	}

	private UpdateOffset() {
		this.bar_circle.style.strokeDashoffset = String(this.offsetValue * (this.reverse ? -1 : 1));
	}

	/**
	 * Handle rotation transform
	 */
	@Property({ computed: "rotate gap", observer: "UpdateRotation" })
	private get rotateValue(): number {
		return (this.rotate - 1) * 90 + (this.gap / 2);
	}

	private UpdateRotation() {
		this.back_circle.setAttribute("transform", `rotate(${this.rotateValue}, 38, 38)`);
		let bar_rotate = this.rotateValue - (this.reverse ? this.gap : 0);
		this.bar_circle.setAttribute("transform", `rotate(${bar_rotate}, 38, 38)`);
	}

	/**
	 * The reverse property was changed, we need to update progress bar styles.
	 */
	private ReverseUpdated() {
		this.UpdateOffset();
		this.UpdateRotation();
	}
}
