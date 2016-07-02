import {Element, Property} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";

/**
 * <gt-progress-circular>
 */
@Element({
	selector: "gt-progress-circular",
	template: "/assets/imports/widgets.html"
})
export class GtProgressCircular extends PolymerElement {
	/**
	 * Length of a full radius circle
	 */
	private static fullRadius = 226;

	/**
	 * Progress bar color
	 */
	@Property
	public color: string = "#64B4FF";

	/**
	 * Background color
	 */
	@Property
	public back: string = "rgba(17, 17, 17, 0.2)";

	/**
	 * Rotation of the progress indicator.
	 * Each unit is 90 deg.
	 */
	@Property
	public rotate: number = 0;

	/**
	 * Width of the gap in degree.
	 */
	@Property
	public gap: number = 0;

	/**
	 * Min value
	 */
	@Property
	public min: number = 0;

	/**
	 * Max value
	 */
	@Property
	public max: number = 1;

	/**
	 * Current value
	 */
	@Property
	public progress: number = 0;

	/**
	 * Reverse the filling direction of the indicator
	 */
	@Property({observer: "ReverseUpdated"})
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
	@Property({computed: "gap", observer: "UpdateGap"})
	private get gapOffset(): number {
		return this.gap / 360 * GtProgressCircular.fullRadius;
	}

	private UpdateGap() {
		this.back_circle.style.strokeDashoffset = String(this.gapOffset);
	}

	/**
	 * Handle offset of the progress bar
	 */
	@Property({computed: "min max progress gapOffset", observer: "UpdateOffset"})
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
	@Property({computed: "rotate gap", observer: "UpdateRotation"})
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
