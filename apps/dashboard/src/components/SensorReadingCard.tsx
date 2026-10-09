
interface Props {
    title: string;
    value?: number;
    unit: string;
    active: boolean; 
    timestamp?: string; 
}

const ACTIVE_LABEL: string = "Active";
const INACTIVE_LABEL: string = "Inactive";

const WAITING_FOR_READING_MESSAGE: string = "Waiting for a reading...";

/**
 * Creates a sensor reading card for displaying information about reading. 
 * 
 * @param param0 group of Props with sensor reading information
 * @returns the sensor card component
 */
export function SensorReadingCard({
    title, 
    value, 
    unit, 
    active, 
    timestamp,
}: Props) {
    return (
        <article className="sensor-card" aria-label={title}>
            <div className="sensor-card__heading">
                <h2>{title}</h2>
                <span 
                    className={`sensor-status ${active ? "sensor-status--active" : ""}`}
                >
                    <span aria-hidden="true" />
                    {active ? ACTIVE_LABEL : INACTIVE_LABEL}
                </span>
            </div>
            <p className="sensor-card__value">
                {value ?? "-"}
                <span>{unit}</span>
            </p>
            <p className="sensor-card__detail">
                {timestamp 
                    ? `Last reading: ${new Date(timestamp).toLocaleTimeString()}`
                    : WAITING_FOR_READING_MESSAGE
                }
            </p>
        </article>
    )
}
