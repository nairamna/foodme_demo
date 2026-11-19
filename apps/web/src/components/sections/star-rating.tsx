import { Star } from "lucide-react";
import { cn } from "@/lib/utils";

const STARS = [1, 2, 3, 4, 5] as const;

/** Read-only stars, announced as "Rated 4 out of 5". */
export function StarRatingDisplay({ rating, className }: { rating: number; className?: string }) {
  return (
    <span
      role="img"
      aria-label={`Rated ${rating} out of 5`}
      className={cn("inline-flex items-center gap-0.5", className)}
    >
      {STARS.map((value) => (
        <Star
          key={value}
          aria-hidden="true"
          size={16}
          strokeWidth={1.75}
          className={value <= rating ? "fill-amber-400 text-amber-400" : "fill-none text-zinc-300"}
        />
      ))}
    </span>
  );
}

/**
 * Star picker built on native radio buttons, so mouse, touch, arrow keys and
 * screen readers all work without extra code.
 */
export function StarRatingInput({
  value,
  onChange,
  disabled,
}: {
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
}) {
  return (
    <fieldset disabled={disabled} className="border-0 p-0">
      <legend className="mb-2 text-sm font-semibold text-zinc-800">Your rating</legend>
      <div className="flex items-center gap-1">
        {STARS.map((star) => (
          <label
            key={star}
            className="cursor-pointer rounded-full p-1 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-zinc-900"
          >
            <input
              type="radio"
              name="order-rating"
              value={star}
              checked={value === star}
              onChange={() => onChange(star)}
              className="sr-only"
              aria-label={star === 1 ? "1 star" : `${star} stars`}
            />
            <Star
              aria-hidden="true"
              size={32}
              strokeWidth={1.5}
              className={cn(
                "transition-colors",
                star <= value ? "fill-amber-400 text-amber-400" : "fill-none text-zinc-300 hover:text-amber-300",
              )}
            />
          </label>
        ))}
      </div>
    </fieldset>
  );
}
