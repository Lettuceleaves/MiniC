import type { JavaMirrorFile } from "./javaMirror";

export interface MirrorComponentProps {
  readonly mirror?: JavaMirrorFile;
}

export function createMirrorComponent(defaultMirror: JavaMirrorFile) {
  return function MirrorComponent({ mirror = defaultMirror }: MirrorComponentProps) {
    return (
      <section className="uiweb-mirror" data-java-source={mirror.javaPath}>
        <header className="uiweb-mirror__header">
          <span className="uiweb-mirror__title">{mirror.exportName}</span>
          <span className="uiweb-mirror__path">{mirror.javaPath}</span>
        </header>
        <div className="uiweb-mirror__grid">
          <dl>
            <dt>package</dt>
            <dd>{mirror.packageName}</dd>
            <dt>kind</dt>
            <dd>{mirror.kind}</dd>
          </dl>
          <div>
            <h2>Methods</h2>
            <ul>
              {mirror.methods.map((method) => (
                <li key={method.signature}>{method.signature}</li>
              ))}
            </ul>
          </div>
          <div>
            <h2>Fields</h2>
            <ul>
              {mirror.fields.map((field) => (
                <li key={field.signature}>{field.signature}</li>
              ))}
            </ul>
          </div>
        </div>
      </section>
    );
  };
}
